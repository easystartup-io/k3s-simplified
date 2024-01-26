package io.easystartup.natgateway;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.cloud.hetzner.firewall.Firewall;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NatGatewayConfig;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.SSH;
import io.easystartup.utils.TemplateUtil;
import io.easystartup.utils.Util;
import me.tomsdevsn.hetznercloud.objects.general.Location;
import me.tomsdevsn.hetznercloud.objects.general.Network;
import me.tomsdevsn.hetznercloud.objects.general.SSHKey;
import me.tomsdevsn.hetznercloud.objects.general.Server;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static io.easystartup.utils.ServerUtils.waitForServerToComeUp;
import static io.easystartup.utils.Util.replaceTildaWithFullHomePath;
import static org.apache.commons.lang3.StringUtils.isBlank;

/*
 * @author indianBond
 */
public class CreateNatGateway {
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;

    private final String configurationFilePath;

    private final SSH ssh;

    private Network hetznerCreatedNetwork;
    private SSHKey hetznerCreatedSSHKey;

    public CreateNatGateway(MainSettings mainSettings, String configurationFilePath) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());

        configurationFilePath = replaceTildaWithFullHomePath(configurationFilePath);

        this.configurationFilePath = configurationFilePath;

        List<String> errors = new ArrayList<>();
        validateNatGatewaySettings(errors);

        if (CollectionUtils.isNotEmpty(errors)) {
            System.out.println("INVALID NAT GATEWAY SETTINGS");
            for (String error : errors) {
                System.out.println(error);
            }
            System.exit(1);
        }

    }

    private void validateNatGatewaySettings(List<String> errors) {
        if (mainSettings.getNatGatewayConfig() == null) {
            errors.add("No nat gateway config present!");
            return;
        }
        NatGatewayConfig natGatewayConfigConfig = mainSettings.getNatGatewayConfig();
        if (natGatewayConfigConfig.getNode() == null) {
            errors.add("No nat gateway node config present!");
            return;
        }
    }

    public void initialize() {
        ConsoleColors.println("\n=== Creating nat gateway===\n", ConsoleColors.BLUE_BOLD);
        hetznerCreatedNetwork = findOrCreateNetwork();
        hetznerCreatedSSHKey = createSSH();

        createFirewall();

        Server server = createServer();

        waitForServerToComeUp(server, ssh, mainSettings);

        installItems(server);

        addRouteToNetwork(server);

        ConsoleColors.println("\n=== Finished creating nat gateway===\n", ConsoleColors.BLUE_BOLD);

        System.out.println(String.format("Nat gateway has been created with ip %s and route has been added to the network", server.getPrivateNet().getFirst().getIp()));
    }

    private void createFirewall() {
        // Todo: create firewall for nat-gateway

    }

    private void addRouteToNetwork(Server server) {
        String natGatewayIP = server.getPrivateNet().getFirst().getIp();

        io.easystartup.cloud.hetzner.network.Network network = new io.easystartup.cloud.hetzner.network.Network(hetznerClient);
        network.addRouteToNetwork(hetznerCreatedNetwork.getId(), "0.0.0.0/0", natGatewayIP);
    }

    private void installItems(Server server) {
        doBasicSetup(server);
    }

    private void doBasicSetup(Server server) {
        System.out.println("Doing nat gateway setup");
        Map<String, Object> map = new HashMap<>();
        map.put("private_network_setup", mainSettings.getPrivateNetworkSubnet());
        String command = TemplateUtil.renderTemplate(TemplateUtil.NAT_GATEWAY_SETUP, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());
        System.out.println(output);
        System.out.println("Finished doing nat-gateway setup");
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

    private Server createServer() {
        NodePool node = mainSettings.getNatGatewayConfig().getNode();
        return createNatGateway(node);
    }

    private Server createNatGateway(NodePool nodePool) {
        String clusterName = mainSettings.getClusterName();
        String instanceType = nodePool.getInstanceType();
        String name = isBlank(nodePool.getName()) ? "nat-gateway" : nodePool.getName();
        String nodeName = String.format("%s-%s-%s-nat-gateway", name, clusterName, instanceType);
        String image = isBlank(nodePool.getImage()) ? mainSettings.getImage() : nodePool.getImage();
        String[] additionalPackages = nodePool.getAdditionalPackages();
        String[] postCreateCommands = nodePool.getPostCreateCommands();
        String location = nodePool.getLocation();
        Server server = hetznerClient.findServer(nodeName);
        if (server != null) {
            System.out.println(ConsoleColors.GREEN + "Server " + nodeName + " already exists, skipping." + ConsoleColors.RESET);
            return server;
        }
        System.out.println("Creating nat-gateway server " + nodeName + "...");
        server = hetznerClient.createServer(
                clusterName,
                instanceType,
                nodeName,
                image,
                additionalPackages == null ? List.of() : Arrays.stream(additionalPackages).toList(),
                postCreateCommands == null ? List.of() : Arrays.stream(postCreateCommands).toList(),
                null,
                hetznerCreatedNetwork,
                hetznerCreatedSSHKey,
                null,
                true,
                true,
                location,
                mainSettings.getSnapshotOs(),
                mainSettings.getSshPort(),
                "nat-gateway",
                mainSettings.isDebug());
        System.out.println("...server " + nodeName + " created.");
        return server;
    }

}
