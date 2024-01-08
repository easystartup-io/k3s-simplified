package io.easystartup;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.configuration.MainSettings;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @author indianBond
 */
public class CreateNewCluster {
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;

    private Network network;
    private Firewall firewall;
    private SSHKey sshKey;

    public CreateNewCluster(MainSettings mainSettings) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
    }

    public void initializeCluster() {
        System.out.println("\n=== Creating infrastructure resources ===\n");
        network = findOrCreateNetwork();
        firewall = createFirewall();
        sshKey = createSSH();

        createServers();
        createLoadBalancer();
    }

    private void createLoadBalancer() {

    }

    private void createServers() {
        List<Server> serverList = new ArrayList<>();

        initializeMasters(serverList);
        initializeWorkerNodes(serverList);
    }

    private void initializeWorkerNodes(List<Server> serverList) {

    }

    private void initializeMasters(List<Server> serverList) {
        PlacementGroup placementGroup = createPlacementGroup();

        long instanceCount = mainSettings.getMastersPool().getInstanceCount();
        for (int i = 0; i < instanceCount; i++) {
            Server masterServer = createMasterServer(placementGroup, i);
            serverList.add(masterServer);
        }
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
            System.out.println("Server " + masterName + " already exists, skipping.");
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
                "master"
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

    private PlacementGroup createPlacementGroup() {
        String name = mainSettings.getClusterName() + "-masters";
        PlacementGroup placementGroup = hetznerClient.findPlacementGroup(name);
        if (placementGroup != null) {
            System.out.println("Placement group " + name + " already exists, skipping.");
            return placementGroup;
        }
        System.out.println("Creating placement group " + name + "...");
        placementGroup = hetznerClient.createPlacementGroup(name);
        System.out.println("done creating " + placementGroup.getName());
        return placementGroup;
    }

    private SSHKey createSSH() {
        String clusterName = mainSettings.getClusterName();
        String publicSSHKeyPath = mainSettings.getPublicSSHKeyPath();
        SSHKey sshKey = hetznerClient.getSSHKey(publicSSHKeyPath);
        if (sshKey != null) {
            System.out.println("SSH Key already exists, skipping.");
            return sshKey;
        }
        System.out.println("Creating SSH Key...");
        return hetznerClient.createSSHKey(clusterName, publicSSHKeyPath);
    }


    private Firewall createFirewall() {
        String firewallName = mainSettings.getClusterName();
        String[] sshAllowedNetworks = mainSettings.getSSHAllowedNetworks();
        String[] apiAllowedNetworks = mainSettings.getAPIAllowedNetworks();
        boolean highAvailability = mainSettings.getMastersPool().getInstanceCount() > 1;
        int sshPort = mainSettings.getSshPort();
        String privateNetworkSubnet = mainSettings.getPrivateNetworkSubnet();

        Firewall fireWall = hetznerClient.findFireWall(firewallName);
        if (fireWall != null) {
            System.out.println("Updating firewall...");
            hetznerClient.updateFirewall(fireWall.getId(), sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        } else {
            System.out.println("Creating firewall...");
            fireWall = hetznerClient.createFirewall(firewallName, sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        }
        return fireWall;
    }

    private Network findOrCreateNetwork() {
        String existingNetworkName = mainSettings.getExistingNetworkName();
        if (StringUtils.isNotBlank(existingNetworkName)) {
            return hetznerClient.findNetwork(existingNetworkName);
        }

        return createNetwork();
    }

    private Network createNetwork() {
        String networkName = mainSettings.getClusterName();
        String location = mainSettings.getMastersPool().getLocation();
        String privateNetworkSubnet = mainSettings.getPrivateNetworkSubnet();

        Network network = hetznerClient.findNetwork(networkName);
        if (network != null) {
            System.out.println("Network " + networkName + " exists");
            return network;
        }
        System.out.println("Creating network");
        Location hetznerLocation = hetznerClient.getLocation(location);
        return hetznerClient.createNetwork(networkName, privateNetworkSubnet, hetznerLocation.getNetworkZone());
    }
}
