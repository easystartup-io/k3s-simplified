package io.easystartup.installer;

import io.easystartup.CreateNewCluster;
import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.SSHUtil;
import io.easystartup.utils.TemplateUtil;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.easystartup.utils.Util.sleep;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/*
 * @author indianBond
 */
public class KubernetesInstaller {
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private final LoadBalancer loadBalancer;
    private final Map<CreateNewCluster.ServerType, List<Server>> servers;

    private final Network network;
    private final Firewall firewall;
    private final SSHKey sshKey;

    public KubernetesInstaller(MainSettings mainSettings, HetznerClient hetznerClient, LoadBalancer loadBalancer, Map<CreateNewCluster.ServerType, List<Server>> servers, Network network, Firewall firewall, SSHKey sshKey) {
        this.mainSettings = mainSettings;
        this.hetznerClient = hetznerClient;
        this.loadBalancer = loadBalancer;
        this.servers = servers;
        this.network = network;
        this.firewall = firewall;
        this.sshKey = sshKey;
    }


    public void startInstallation() {
        System.out.println("\n=== Setting up Kubernetes ===\n");

        List<Server> serverList = servers.get(CreateNewCluster.ServerType.MASTER);
        setUpFirstMaster(serverList.getFirst());
        setUpOtherMasters(serverList);
        setUpWorkers(servers.get(CreateNewCluster.ServerType.WORKER));

        System.out.println("\n=== Deploying Hetzner drivers ===\n");

        Util.checkKubectl();

        addLabelsAndTaintsToMasters();
        addLabelsAndTaintsToWorkers();

        createHetznerCloudSecret();
        deployCloudControllerManager();
        deployCsiDriver();
        deploySystemUpgradeController();
        deployClusterAutoscaler();
    }

    private void setUpFirstMaster(Server firstMaster) {
        System.out.println("Deploying k3s to first master " + firstMaster.getName() + "...");

        String output = SSHUtil.ssh(firstMaster, mainSettings.getSshPort(), masterInstallScript(firstMaster, true), mainSettings.isUseSSHAgent());

        System.out.println("Waiting for the control plane to be ready...");

        sleep(10000); // Sleep for 10 seconds

        saveKubeconfig();

        System.out.println("...k3s has been deployed to first master " + firstMaster.getName() + " and the control plane is up.");
    }

    private void setUpOtherMasters(List<Server> masters) {
        if (masters.size() < 2) {
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(masters.size() - 1);
        List<Future<?>> futures = new ArrayList<>();

        for (Server master : masters.subList(1, masters.size())) {
            futures.add(executor.submit(() -> deployK3sToMaster(master)));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
    }

    private void setUpWorkers(List<Server> workers) {
        if (CollectionUtils.isEmpty(workers)){
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());
        List<Future<?>> futures = new ArrayList<>();

        for (Server worker : workers) {
            futures.add(executor.submit(() -> deployK3sToWorker(worker)));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
    }

    private void deployK3sToMaster(Server master) {
        System.out.println("Deploying k3s to master " + master.getName() + "...");
        SSHUtil.ssh(master, mainSettings.getSshPort(), masterInstallScript(master), mainSettings.isUseSSHAgent());
        System.out.println("...k3s has been deployed to master " + master.getName() + ".");
    }

    private void deployK3sToWorker(Server worker) {
        System.out.println("Deploying k3s to worker " + worker.getName() + "...");
        ssh.run(worker, mainSettings.getSshPort(), workerInstallScript(), mainSettings.isUseSSHAgent());
        System.out.println("...k3s has been deployed to worker " + worker.getName() + ".");
    }

    private void createHetznerCloudSecret() {
        System.out.println("\nCreating secret for Hetzner Cloud token...");

        String secretManifest = renderTemplate(HETZNER_CLOUD_SECRET_MANIFEST, settings.getHetznerToken());
        String command = buildKubectlApplyCommand(secretManifest);

        SSHUtil.ShellResult result = SSHUtil.ssh(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to create Hetzner Cloud secret:");
            System.out.println(result);
            throw new RuntimeException("Failed to create Hetzner Cloud secret");
        }

        System.out.println("...secret created.");
    }

    private void deployCloudControllerManager() {
        System.out.println("\nDeploying Hetzner Cloud Controller Manager...");

        try {
            String ccmManifest = downloadManifest(settings.getCloudControllerManagerManifestUrl());
            ccmManifest = ccmManifest.replace("--cluster-cidr=[^\"+]", "--cluster-cidr=" + settings.getClusterCidr());

            String ccmManifestPath = "/tmp/ccm_manifest.yaml";
            writeFile(ccmManifestPath, ccmManifest);

            String command = "kubectl apply -f " + ccmManifestPath;
            ShellResult result = Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

            if (!result.isSuccess()) {
                System.out.println("Failed to deploy Cloud Controller Manager:");
                System.out.println(result);
                throw new RuntimeException("Failed to deploy Cloud Controller Manager");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error in deploying Cloud Controller Manager", e);
        }

        System.out.println("...Cloud Controller Manager deployed");
    }

    private void deployCsiDriver() {
        System.out.println("\nDeploying Hetzner CSI Driver...");

        String command = "kubectl apply -f " + settings.getCsiDriverManifestUrl();
        ShellResult result = Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to deploy CSI Driver:");
            System.out.println(result);
            throw new RuntimeException("Failed to deploy CSI Driver");
        }

        System.out.println("...CSI Driver deployed");
    }

    private void deploySystemUpgradeController() {
        System.out.println("\nDeploying k3s System Upgrade Controller...");

        String command = "kubectl apply -f " + settings.getSystemUpgradeControllerManifestUrl();
        ShellResult result = Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to deploy k3s System Upgrade Controller:");
            System.out.println(result);
            throw new RuntimeException("Failed to deploy k3s System Upgrade Controller");
        }

        System.out.println("...k3s System Upgrade Controller deployed.");
    }

    private void deployClusterAutoscaler() {
        System.out.println("\nDeploying Cluster Autoscaler...");

        if (autoscalingWorkerNodePools.isEmpty()) {
            return;
        }

        try {
            String nodePoolArgs = buildNodePoolArgs(autoscalingWorkerNodePools);
            String clusterAutoscalerManifest = renderTemplate(CLUSTER_AUTOSCALER_MANIFEST, nodePoolArgs);
            String clusterAutoscalerManifestPath = "/tmp/cluster_autoscaler_manifest.yaml";

            writeFile(clusterAutoscalerManifestPath, clusterAutoscalerManifest);

            String command = "kubectl apply -f " + clusterAutoscalerManifestPath;
            ShellResult result = Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

            if (!result.isSuccess()) {
                System.out.println("Failed to deploy Cluster Autoscaler:");
                System.out.println(result);
                throw new RuntimeException("Failed to deploy Cluster Autoscaler");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error in deploying Cluster Autoscaler", e);
        }

        System.out.println("...Cluster Autoscaler deployed.");
    }

    private void addLabelsAndTaintsToMasters() {
        System.out.println("\nAdding labels and taints to masters...");

        String nodeNames = masters.stream().map(Server::getName).collect(Collectors.joining(" "));
        String allMarks = settings.getMastersPool().getLabels().stream()
                .map(label -> label.getKey() + "=" + label.getValue())
                .collect(Collectors.joining(" "));

        String command = "kubectl label --overwrite nodes " + nodeNames + " " + allMarks;
        Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

        System.out.println("...done.");
    }

    private void addLabelsAndTaintsToWorkers() {
        System.out.println("\nAdding labels and taints to workers...");
        List<Server> workers = servers.get(CreateNewCluster.ServerType.WORKER);

        for (NodePool nodePool : mainSettings.getWorkerNodePools()) {
            String instanceType = nodePool.getInstanceType();
            String nodePoolRegex = mainSettings.getClusterName() + "-" + instanceType + "-pool-" + nodePool.getName() + "-worker";
            Pattern pattern = Pattern.compile(nodePoolRegex);

            List<Server> nodes = workers.stream()
                    .filter(worker -> pattern.matcher(worker.getName()).matches())
                    .collect(Collectors.toList());

            String nodeNames = nodes.stream().map(Server::getName).collect(Collectors.joining(" "));
            String allMarks = Arrays.stream(nodePool.getLabels())
                    .map(label -> label.getKey() + "=" + label.getValue())
                    .collect(Collectors.joining(" "));

            String command = "kubectl label --overwrite nodes " + nodeNames + " " + allMarks;
            Util.shellRun(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
        }

        System.out.println("...done.");
    }

    private String generateTlsSans() {
        // Using linked hash set so as to not get duplicates
        Set<String> sans = new LinkedHashSet<>();
        sans.add("--tls-san=" + getApiServerIpAddress());

        // Adding in case load balancer api server is private, but still adding possibility to whitelist public ip
        if (loadBalancer != null && isNotBlank(loadBalancer.getPublicIpv4())) {
            sans.add("--tls-san=" + loadBalancer.getPublicIpv4());
        }

        if (isNotBlank(mainSettings.getApiServerHostname())) {
            sans.add("--tls-san=" + mainSettings.getApiServerHostname());
        }
        List<Server> masters = servers.get(CreateNewCluster.ServerType.MASTER);
        if (masters.size() > 1 && loadBalancer != null) {
            sans.add("--tls-san=" + loadBalancer.getPrivateNet().getFirst().getIp());
        }

        for (Server master : masters) {
            String masterPrivateIp = master.getPrivateNet().getFirst().getIp();
            sans.add("--tls-san=" + masterPrivateIp);
        }

        return String.join(" ", sans);
    }

    private String masterInstallScript(Server master, boolean firstMaster) {
        String server = firstMaster ? " --cluster-init " : " --server https://" + getApiServerIpAddress() + ":6443 ";
        String flannelBackend = findFlannelBackend();
        String extraArgs = computeExtraArgs();
        String taint = mainSettings.isScheduleWorkloadsOnMasters() ? " " : " --node-taint CriticalAddonsOnly=true:NoExecute ";

        Map<String, Object> map = new HashMap<>();
        map.put("cluster_name", mainSettings.getClusterName());
        map.put("k3s_version", mainSettings.getK3SVersion());
        map.put("k3s_token", getK3sToken());
        map.put("disable_flannel", String.valueOf(mainSettings.getDisableFlannel()));
        map.put("flannel_backend", flannelBackend);
        map.put("taint", taint);
        map.put("extra_args", extraArgs);
        map.put("server", server);
        map.put("tls_sans", generateTlsSans());
        map.put("private_network_test_ip", getPrivateNetworkTestIp(mainSettings.getPrivateNetworkSubnet()));
        map.put("cluster_cidr", mainSettings.getClusterCIDR());
        map.put("service_cidr", mainSettings.getServiceCIDR());
        map.put("cluster_dns", mainSettings.getClusterDNS());
        TemplateUtil.renderTemplate(TemplateUtil.MASTER_INSTALL_SCRIPT, map);
    }

    private String getK3sToken() {
        List<Server> serverList = servers.get(CreateNewCluster.ServerType.MASTER);
        Server firstMaster = serverList.getFirst();
        String command = "cat /var/lib/rancher/k3s/server/node-token";
        String token = sshUtil.executeCommand(getHostIp(firstMaster), mainSettings.getSshPort(), mainSettings.isUseSSHAgent(), mainSettings.getPrivateSSHKeyPath(), command);

        if (token == null || token.isEmpty()) {
            token = new BigInteger(130, new SecureRandom()).toString(32); // Random token generation
        }

        return token;
    }

    private String getApiServerIpAddress() {
        List<Server> masterServer = servers.get(CreateNewCluster.ServerType.MASTER);
        if (masterServer.size() > 1 && loadBalancer != null) {
            if (mainSettings.isPrivateApiLoadBalancer()
                    && isNotEmpty(loadBalancer.getPrivateNet())
                    && isNotBlank(loadBalancer.getPrivateNet().getFirst().getIp())) {
                return loadBalancer.getPrivateNet().getFirst().getIp();
            } else if (Boolean.TRUE.equals(loadBalancer.getPublicEnabled()) && StringUtils.isNotBlank(loadBalancer.getPublicIpv4())) {
                return loadBalancer.getPublicIpv4();
            }
        }
        return getHostIp(masterServer.getFirst());
    }

    private String getHostIp(Server server) {
        PublicNet publicNet = server.getPublicNet();
        if (publicNet != null && publicNet.getIpv4() != null && isNotBlank(publicNet.getIpv4().getIp())) {
            return publicNet.getIpv4().getIp();
        }
        List<PrivateNet> privateNet = server.getPrivateNet();
        if (isNotEmpty(privateNet)) {
            return privateNet.getFirst().getIp();
        }
        return null;
    }

    private String workerInstallScript() {
        // Template rendering logic goes here
        // Placeholder for rendered script
        return "Rendered Worker Install Script";
    }

    private String findFlannelBackend() {
        if (!mainSettings.isEnableEncryption()) {
            return " ";
        }

        // We support everything beyond 1.23 so flannel latest is fine
        return " --flannel-backend=wireguard-native ";
    }

    public static String getPrivateNetworkTestIp(String privateNetworkSubnet) {
        // Split the subnet string at periods
        String[] parts = privateNetworkSubnet.split("\\.")[0].split("/")[0].split("\\.");

        // Take the first three parts and join them with periods
        String joinedParts = String.join(".", parts[0], parts[1], parts[2]);

        // Append ".1" to the end
        return joinedParts + ".1";
    }

    public String computeExtraArgs() {
        String apiServerArgs = buildArgsList("kube-apiserver", mainSettings.getKubeApiServerArgs());
        String schedulerArgs = buildArgsList("kube-scheduler", mainSettings.getKubeSchedulerArgs());
        String kubeControllerManager = buildArgsList("kube-controller-manager", mainSettings.getKubeControllerManagerArgs());
        String kubelet = buildArgsList("kubelet", mainSettings.getKubeletArgs());
        String kubeProxy = buildArgsList("kube-proxy", mainSettings.getKubeProxyArgs());

        return apiServerArgs + schedulerArgs + kubeControllerManager + kubelet + kubeProxy;
    }

    private String buildArgsList(String component, String[] args) {
        return Arrays.stream(args)
                .map(arg -> " --" + component + "-arg=\"" + arg + "\" ")
                .collect(Collectors.joining());
    }
}
