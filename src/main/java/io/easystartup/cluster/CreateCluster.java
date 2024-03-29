package io.easystartup.cluster;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.cloud.hetzner.loadbalancer.Loadbalancer;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.kubernetes.KubernetesInstaller;
import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.SSH;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.*;

import static io.easystartup.utils.ServerUtils.waitForServerToComeUp;
import static io.easystartup.utils.Util.sleep;
import static org.apache.commons.lang3.StringUtils.isBlank;

/*
 * @author indianBond
 */
public class CreateCluster {

    public enum ServerType {
        MASTER,
        WORKER
    }

    /**
     * <a href="https://docs.hetzner.com/cloud/placement-groups/overview#limits">...</a>
     */
    private static final int MAX_INSTANCES_PER_PLACEMENT_GROUP = 10;
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private LoadBalancer loadBalancer;
    private final Map<ServerType, List<Server>> serverMap = new ConcurrentHashMap();

    private Network network;
    private Firewall firewall;
    private SSHKey sshKey;

    private final SSH ssh;
    private final ExecutorService VIRTUAL_THREAD_PER_TASK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final int MAX_API_CALL_CONCURRENCY = 5;

    public CreateCluster(MainSettings mainSettings) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());
    }

    public void initializeCluster() {
        System.out.println(ConsoleColors.BLUE_BOLD + "\n=== Creating infrastructure resources ===\n" + ConsoleColors.RESET);
        network = findOrCreateNetwork();
        firewall = createFirewall();
        sshKey = createSSH();

        createServers(serverMap);
        loadBalancer = createLoadBalancer();

        waitForAllServersToComeUp(serverMap);

        KubernetesInstaller kubernetesInstaller = new KubernetesInstaller(mainSettings, hetznerClient, loadBalancer, serverMap, network, firewall, sshKey);
        kubernetesInstaller.startInstallation();

        System.out.println(ConsoleColors.BLUE_BOLD + "%n=== Finished creating infrastructure resources ===%n" + ConsoleColors.RESET);
        System.out.println("Your cluster is now created\n");
        System.out.println("To connect to kubernetes run --\n");
        System.out.printf("kubectl --kubeconfig=%s get nodes%n%n", mainSettings.getKubeconfigPath());
    }

    private LoadBalancer createLoadBalancer() {
        if (mainSettings.getMastersPool().getInstanceCount() == 1) {
            return null;
        }
        Loadbalancer loadbalancer = new Loadbalancer(hetznerClient);
        String loadBalancerName = mainSettings.getClusterName() + "-api";
        LoadBalancer loadBalancer = loadbalancer.find(loadBalancerName);
        if (loadBalancer != null) {
            System.out.println("Load balancer has private IP : " + ConsoleColors.BLUE + loadBalancer.getPrivateNet().getFirst().getIp() + ConsoleColors.RESET);
            if (!mainSettings.isPrivateApiLoadBalancer()) {
                System.out.println("Load balancer has public IP : " + loadBalancer.getPublicIpv4());
            }
            System.out.println(ConsoleColors.GREEN + "Load balancer for API server already exists, skipping." + ConsoleColors.RESET);
            return loadBalancer;
        }
        System.out.println("Creating load balancer for API server...");
        loadBalancer = loadbalancer.createK8sAPILoadBalancer(mainSettings.getClusterName(),
                network.getId(),
                mainSettings.isPrivateApiLoadBalancer(),
                mainSettings.getMastersPool().getLocation()
        );
        if (!mainSettings.isPrivateApiLoadBalancer()) {
            while (true) {
                loadBalancer = loadbalancer.find(loadBalancerName);
                if (loadBalancer != null && StringUtils.isNotBlank(loadBalancer.getPublicIpv4())) {
                    break;
                }
                sleep(2000);
            }
        }
        if (mainSettings.isPrivateApiLoadBalancer()) {
            System.out.println("Load balancer has private IP " + loadBalancer.getPrivateNet().getFirst().getIp());
        } else {
            System.out.println("Load balancer has private IP : " + loadBalancer.getPrivateNet().getFirst().getIp());
            System.out.println("Load balancer has public IP : " + loadBalancer.getPublicIpv4());
        }
        System.out.println(ConsoleColors.GREEN + "Done" + ConsoleColors.RESET);
        return loadBalancer;
    }


    private void createServers(Map<ServerType, List<Server>> serverList) {
        for (ServerType value : ServerType.values()) {
            serverList.putIfAbsent(value, new CopyOnWriteArrayList<>());
        }
        initializeMasters(serverList.get(ServerType.MASTER));
        initializeWorkerNodes(serverList.get(ServerType.WORKER));
    }

    /**
     * Create worker nodes which don't need autoscaling.
     * Autoscaling nodes will be created using kubernetes hetzner autoscaler
     */
    private void initializeWorkerNodes(List<Server> serverList) {
        NodePool[] workerNodePools = mainSettings.getWorkerNodePools();

        List<NodePool> noAutoscalingWorkerNodePools = Arrays.stream(workerNodePools).toList().stream()
                .filter(nodePool -> !(nodePool.getAutoScaling() != null && nodePool.getAutoScaling().isEnabled()))
                .toList();

        Semaphore semaphore = new Semaphore(MAX_API_CALL_CONCURRENCY);
        List<Future<Server>> futures = new ArrayList<>();
        for (NodePool nodePool : noAutoscalingWorkerNodePools) {
            List<PlacementGroup> placementGroups = createPlacementGroupsForNodePool(nodePool);
            for (int index = 0; index < nodePool.getInstanceCount(); index++) {
                PlacementGroup placementGroup = placementGroups.get(index % placementGroups.size());
                int finalIndex = index;
                executeInsideSemaphore(semaphore, () -> {
                    Future<Server> submit = VIRTUAL_THREAD_PER_TASK_EXECUTOR.submit(() ->
                            createWorkerServer(finalIndex, nodePool, placementGroup)
                    );
                    futures.add(submit);
                });
            }
        }
        waitAndAddToServerList(serverList, futures);
    }

    private void executeInsideSemaphore(Semaphore semaphore, Runnable runnable) {
        try {
            semaphore.acquire();
            runnable.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    private Server createWorkerServer(int index, NodePool nodePool, PlacementGroup placementGroup) {
        String clusterName = mainSettings.getClusterName();
        String instanceType = nodePool.getInstanceType();
        String nodeName = String.format("%s-%s-pool-%s-worker%s", clusterName, instanceType, nodePool.getName(), index + 1);
        String image = isBlank(nodePool.getImage()) ? mainSettings.getImage() : nodePool.getImage();
        String[] additionalPackages = nodePool.getAdditionalPackages() == null ?
                mainSettings.getAdditionalPackages() : nodePool.getAdditionalPackages();
        String[] postCreateCommands = nodePool.getPostCreateCommands() == null ?
                mainSettings.getPostCreateCommands() : nodePool.getPostCreateCommands();
        String location = nodePool.getLocation();
        Server server = hetznerClient.findServer(nodeName);
        if (server != null) {
            System.out.println(ConsoleColors.GREEN + "Server " + nodeName + " already exists, skipping." + ConsoleColors.RESET);
            return server;
        }
        System.out.println("Creating server " + nodeName + "...");
        server = hetznerClient.createServer(
                clusterName,
                instanceType,
                nodeName,
                image,
                additionalPackages == null ? List.of() : Arrays.stream(additionalPackages).toList(),
                postCreateCommands == null ? List.of() : Arrays.stream(postCreateCommands).toList(),
                firewall,
                network,
                sshKey,
                placementGroup,
                mainSettings.isEnablePublicNetIpv4(),
                mainSettings.isEnablePublicNetIpv6(),
                location,
                mainSettings.getSnapshotOs(),
                mainSettings.getSshPort(),
                "worker",
                mainSettings.isDebug());
        System.out.println("...server " + nodeName + " created.");
        return server;
    }

    private List<PlacementGroup> createPlacementGroupsForNodePool(NodePool nodePool) {
        int placementGroupsCount = (int) Math.ceil((double) nodePool.getInstanceCount() / MAX_INSTANCES_PER_PLACEMENT_GROUP);
        List<PlacementGroup> placementGroups = new ArrayList<>();

        for (int index = 1; index <= placementGroupsCount; index++) {
            String placementGroupName = mainSettings.getClusterName() + "-" + nodePool.getName() + "-" + index;
            PlacementGroup placementGroup = createPlacementGroup(placementGroupName);
            placementGroups.add(placementGroup);
        }

        return placementGroups;
    }

    private void initializeMasters(List<Server> serverList) {
        String placementGroupName = mainSettings.getClusterName() + "-masters";
        PlacementGroup placementGroup = createPlacementGroup(placementGroupName);

        Semaphore semaphore = new Semaphore(MAX_API_CALL_CONCURRENCY);

        long instanceCount = mainSettings.getMastersPool().getInstanceCount();

        List<Future<Server>> futures = new ArrayList<>();
        for (int i = 0; i < instanceCount; i++) {
            int finalI = i;
            if (i == 0) {
                Server masterServer = createMasterServer(placementGroup, finalI);
                serverList.add(masterServer);
            } else {
                executeInsideSemaphore(semaphore, () -> {
                    Future<Server> future = VIRTUAL_THREAD_PER_TASK_EXECUTOR.submit(() ->
                        createMasterServer(placementGroup, finalI)
                    );
                    futures.add(future);
                });
            }
        }
        waitAndAddToServerList(serverList, futures);
    }

    private Server createMasterServer(PlacementGroup placementGroup, int i) {
        String clusterName = mainSettings.getClusterName();
        String instanceType = mainSettings.getMastersPool().getInstanceType();
        String masterName = String.format("%s-%s-master%s", clusterName, instanceType, i + 1);
        String image = getMasterImage();
        String[] additionalPackages = getMasterAdditionalPackage();
        String[] masterPostCreateCommands = getMasterPostCreateCommands();
        String location = mainSettings.getMastersPool().getLocation();
        Server server = hetznerClient.findServer(masterName);
        if (server != null) {
            System.out.println(ConsoleColors.GREEN + "Server " + masterName + " already exists, skipping." + ConsoleColors.RESET);
            return server;
        }
        System.out.println("Creating server " + masterName + "...");
        server = hetznerClient.createServer(
                clusterName,
                instanceType,
                masterName,
                image,
                additionalPackages == null ? List.of() : Arrays.stream(additionalPackages).toList(),
                masterPostCreateCommands == null ? List.of() : Arrays.stream(masterPostCreateCommands).toList(),
                firewall,
                network,
                sshKey,
                placementGroup,
                mainSettings.isEnablePublicNetIpv4(),
                mainSettings.isEnablePublicNetIpv6(),
                location,
                mainSettings.getSnapshotOs(),
                mainSettings.getSshPort(),
                "master",
                mainSettings.isDebug()
        );
        System.out.println("...server " + masterName + " created.");
        return server;
    }

    private String getMasterImage() {
        String image = mainSettings.getMastersPool().getImage();
        return image == null ? mainSettings.getImage() : image;
    }

    private String[] getMasterAdditionalPackage() {
        String[] additionalPackages = mainSettings.getMastersPool().getAdditionalPackages();
        return additionalPackages == null ? mainSettings.getAdditionalPackages() : additionalPackages;
    }

    private String[] getMasterPostCreateCommands() {
        String[] postCreateCommands = mainSettings.getMastersPool().getPostCreateCommands();
        return postCreateCommands == null ? mainSettings.getPostCreateCommands() : postCreateCommands;
    }

    private PlacementGroup createPlacementGroup(String placementGroupName) {
        io.easystartup.cloud.hetzner.placementgroup.PlacementGroup pg = new io.easystartup.cloud.hetzner.placementgroup.PlacementGroup(hetznerClient);
        PlacementGroup placementGroup = pg.find(placementGroupName);
        if (placementGroup != null) {
            System.out.println("Placement group " + placementGroupName + " already exists, skipping.");
            return placementGroup;
        }
        System.out.println("Creating placement group " + placementGroupName + "...");
        placementGroup = pg.create(placementGroupName);
        System.out.println("done creating placement group " + placementGroup.getName());
        return placementGroup;
    }

    private SSHKey createSSH() {
        io.easystartup.cloud.hetzner.ssh.SSHKey keys = new io.easystartup.cloud.hetzner.ssh.SSHKey(hetznerClient);
        String clusterName = mainSettings.getClusterName();
        String publicSSHKeyPath = mainSettings.getPublicSSHKeyPath();
        SSHKey sshKey = keys.find(publicSSHKeyPath);
        if (sshKey != null) {
            System.out.println("SSH Key already exists, skipping.");
            return sshKey;
        }
        System.out.println("Creating SSH Key...");
        return keys.create(clusterName, publicSSHKeyPath);
    }


    private Firewall createFirewall() {
        io.easystartup.cloud.hetzner.firewall.Firewall firewall = new io.easystartup.cloud.hetzner.firewall.Firewall(hetznerClient);
        String firewallName = mainSettings.getClusterName();
        String[] sshAllowedNetworks = mainSettings.getSSHAllowedNetworks();
        String[] apiAllowedNetworks = mainSettings.getAPIAllowedNetworks();
        boolean highAvailability = mainSettings.getMastersPool().getInstanceCount() > 1;
        int sshPort = mainSettings.getSshPort();
        String privateNetworkSubnet = mainSettings.getPrivateNetworkSubnet();

        Firewall fw = firewall.find(firewallName);
        if (fw != null) {
            System.out.println("Updating firewall...");
            firewall.update(fw.getId(), sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        } else {
            System.out.println("Creating firewall...");
            fw = firewall.create(firewallName, sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        }
        return fw;
    }

    private Network findOrCreateNetwork() {
        io.easystartup.cloud.hetzner.network.Network nw = new io.easystartup.cloud.hetzner.network.Network(hetznerClient);
        String existingNetworkName = mainSettings.getExistingNetworkName();
        if (StringUtils.isNotBlank(existingNetworkName)) {
            return nw.find(existingNetworkName);
        }

        return createNetwork();
    }

    private Network createNetwork() {
        io.easystartup.cloud.hetzner.network.Network nw = new io.easystartup.cloud.hetzner.network.Network(hetznerClient);
        String networkName = mainSettings.getClusterName();
        String location = mainSettings.getMastersPool().getLocation();
        String privateNetworkSubnet = mainSettings.getPrivateNetworkSubnet();

        Network network = nw.find(networkName);
        if (network != null) {
            System.out.println("Network " + networkName + " exists");
            return network;
        }
        System.out.println("Creating network");
        io.easystartup.cloud.hetzner.location.Location location1 = new io.easystartup.cloud.hetzner.location.Location(hetznerClient);
        Location hetznerLocation = location1.getLocation(location);
        return nw.create(networkName, privateNetworkSubnet, hetznerLocation.getNetworkZone());
    }

    private void waitForAllServersToComeUp(Map<ServerType, List<Server>> serverMap) {
        for (Map.Entry<ServerType, List<Server>> serverTypeListEntry : serverMap.entrySet()) {
            List<Server> value = serverTypeListEntry.getValue();
            for (Server server : value) {
                waitForServerToComeUp(server, ssh, mainSettings, false);
            }
        }
    }

    private void waitAndAddToServerList(List<Server> serverList, List<Future<Server>> futures) {
        for (Future<Server> server : futures) {
            try {
                serverList.add(server.get());
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }
}
