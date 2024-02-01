package io.easystartup.cluster;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.cloud.hetzner.loadbalancer.Loadbalancer;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.SSH;
import io.easystartup.utils.Util;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * @author indianBond
 */
public class DeleteCluster {

    private static final int MAX_INSTANCES_PER_PLACEMENT_GROUP = 10;
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private final Map<CreateCluster.ServerType, List<Server>> serverMap = new ConcurrentHashMap();

    private final SSH ssh;

    public DeleteCluster(MainSettings mainSettings) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());
    }

    public void deleteCluster() {
        System.out.println(ConsoleColors.BLUE_BOLD + "\n=== Deleting infrastructure resources ===\n" + ConsoleColors.RESET);
        deleteLoadBalancer();

        deleteServers(serverMap);
        findAndDeleteNetworks();
        // Need to delete firewall in the end because it does not allow to delete unless all servers are deleted
        Util.sleep(15_000L);
        deleteFirewall();
        System.out.println("Not deleting SSH, in case its being used with other boxes");

        deleteSSH();
    }

    private void deleteLoadBalancer() {
        if (mainSettings.getMastersPool().getInstanceCount() == 1) {
            return;
        }
        Loadbalancer loadbalancer = new Loadbalancer(hetznerClient);
        String loadBalancerName = mainSettings.getClusterName() + "-api";
        LoadBalancer loadBalancer = loadbalancer.find(loadBalancerName);
        if (loadBalancer != null) {
            loadbalancer.delete(loadBalancer.getId());
        }
    }


    private void deleteServers(Map<CreateCluster.ServerType, List<Server>> serverList) {
        for (CreateCluster.ServerType value : CreateCluster.ServerType.values()) {
            serverList.putIfAbsent(value, new CopyOnWriteArrayList<>());
        }
        deleteMasters(serverList.get(CreateCluster.ServerType.MASTER));
        deleteWorkers(serverList.get(CreateCluster.ServerType.WORKER));
    }

    private void deleteWorkers(List<Server> serverList) {
        NodePool[] workerNodePools = mainSettings.getWorkerNodePools();

        List<NodePool> noAutoscalingWorkerNodePools = Arrays.stream(workerNodePools).toList().stream()
                .filter(nodePool -> !(nodePool.getAutoScaling() != null && nodePool.getAutoScaling().isEnabled()))
                .toList();

        for (NodePool nodePool : noAutoscalingWorkerNodePools) {
            deletePlacementGroup(nodePool);
            for (int index = 0; index < nodePool.getInstanceCount(); index++) {
                deleteWorkerServer(index, nodePool);
            }
        }
    }

    private void deleteWorkerServer(int index, NodePool nodePool) {
        String clusterName = mainSettings.getClusterName();
        String nodeName = String.format("%s-%s-pool-%s-worker%s", clusterName, nodePool.getInstanceType(), nodePool.getName(), index + 1);
        Server server = hetznerClient.findServer(nodeName);
        if (server != null) {
            System.out.println("Deleting node " + nodeName);
            hetznerClient.deleteServer(server.getId());
        }
    }

    private void deletePlacementGroup(NodePool nodePool) {
        int placementGroupsCount = (int) Math.ceil((double) nodePool.getInstanceCount() / MAX_INSTANCES_PER_PLACEMENT_GROUP);

        for (int index = 1; index <= placementGroupsCount; index++) {
            String placementGroupName = mainSettings.getClusterName() + "-" + nodePool.getName() + "-" + index;
            deletePlacementGroup(placementGroupName);
        }
    }

    private void deleteMasters(List<Server> serverList) {
        String placementGroupName = mainSettings.getClusterName() + "-masters";
        deletePlacementGroup(placementGroupName);

        long instanceCount = mainSettings.getMastersPool().getInstanceCount();
        for (int i = 0; i < instanceCount; i++) {
            Server masterServer = deleteMaster(i);
            serverList.add(masterServer);
        }
    }

    private Server deleteMaster(int i) {
        String clusterName = mainSettings.getClusterName();
        String instanceType = mainSettings.getMastersPool().getInstanceType();
        String masterName = String.format("%s-%s-master%s", clusterName, instanceType, i + 1);
        Server server = hetznerClient.findServer(masterName);
        if (server != null) {
            System.out.println("Deleting server " + masterName);
            hetznerClient.deleteServer(server.getId());
            return server;
        }

        return server;
    }

    private void deletePlacementGroup(String placementGroupName) {
        io.easystartup.cloud.hetzner.placementgroup.PlacementGroup pg = new io.easystartup.cloud.hetzner.placementgroup.PlacementGroup(hetznerClient);
        PlacementGroup placementGroup = pg.find(placementGroupName);
        if (placementGroup != null) {
            System.out.println("Deleting placement group " + placementGroupName);
            pg.delete(placementGroup.getId());
        }
    }

    private void deleteSSH() {
        io.easystartup.cloud.hetzner.ssh.SSHKey keys = new io.easystartup.cloud.hetzner.ssh.SSHKey(hetznerClient);
        String clusterName = mainSettings.getClusterName();
        String publicSSHKeyPath = mainSettings.getPublicSSHKeyPath();
        SSHKey sshKey = keys.find(publicSSHKeyPath);
        if (sshKey != null) {
            System.out.println("SSH Key already exists, skipping.");
        }
    }


    private void deleteFirewall() {
        io.easystartup.cloud.hetzner.firewall.Firewall firewall = new io.easystartup.cloud.hetzner.firewall.Firewall(hetznerClient);
        String firewallName = mainSettings.getClusterName();
        Firewall fw = firewall.find(firewallName);
        if (fw != null) {
            System.out.println("Deleting firewall...");
            firewall.delete(fw.getId());
        }
    }

    private void findAndDeleteNetworks() {
        deleteClusterNetwork();
    }

    private void deleteClusterNetwork() {
        io.easystartup.cloud.hetzner.network.Network nw = new io.easystartup.cloud.hetzner.network.Network(hetznerClient);
        String networkName = mainSettings.getClusterName();
        Network network = nw.find(networkName);
        if (network != null) {
            if (CollectionUtils.isNotEmpty(network.getRoutes())) {
                Optional<Route> first = network.getRoutes().stream().filter(val -> val.getDestination().equals("0.0.0.0/0")).findFirst();
                if (first.isPresent()) {
                    System.out.println("Not deleting network, because it contains a route to nat gateway at " + first.get().getGateway());
                    return;
                }
            }
            System.out.println("Network " + networkName + " deleted");
            nw.delete(network.getId());
        }
    }

}
