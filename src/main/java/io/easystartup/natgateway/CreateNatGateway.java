package io.easystartup.natgateway;

import io.easystartup.cloud.hetzner.HetznerClient;
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
            System.out.println("INVALID ACCESS BOX SETTINGS");
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

        // Todo: create firewall for access-box

        Server server = createServer();

        waitForServerToComeUp(server, ssh, mainSettings);

        installItems(server);

        ConsoleColors.println("\n=== Finished creating access box===\n", ConsoleColors.BLUE_BOLD);

        System.out.println("""
                The private and public keys have been copied to ~/.ssh/hetzner_rsa and ~/.ssh/hetzner_rsa.pub
                The cloud_config.yaml has also been copied to the root directory and the ssh_key paths replaced accordingly in the cluster_config.yaml
                And the hetzner token has been copied and set in the file itself
                And the kubeconfig path has changed to './kubeconfig' in the cluster_config.yaml
                """);

        System.out.println(getConfigureNatGatewaySettings(server));

        System.out.println("""
                And after connecting just run 
                k3s-simplified create --config cluster_config.yaml
                """);
    }

    private void installItems(Server server) {
        doBasicSetup(server);
    }

    private void doBasicSetup(Server server) {
        System.out.println("Doing nat gateway setup");
        Map<String, Object> map = new HashMap<>();
        String command = TemplateUtil.renderTemplate(TemplateUtil.NAT_GATEWAY_SETUP, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());
        System.out.println(output);
        System.out.println("Finished doing nat-gateway setup");
    }


    private String getConfigureNatGatewaySettings(Server server) {
        String privateSSHKeyPath = Util.replaceFullHomePathWithTilda(mainSettings.getPrivateSSHKeyPath());
        if (mainSettings.getSshPort() != 22) {
            return String.format("""
                    To connect to nat gateway box run this command,
                                    
                    ssh root@%s -p %s -i %s
                    """, server.getPublicNet().getIpv4().getIp(), mainSettings.getSshPort(), privateSSHKeyPath);
        } else {
            return String.format("""
                    To connect to nat gateway box run this command,
                                    
                    ssh root@%s -i %s
                    """, server.getPublicNet().getIpv4().getIp(), privateSSHKeyPath);
        }
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
        NodePool node = mainSettings.getAccessBoxConfig().getNode();
        return createAccessBox(node);
    }

    private Server createAccessBox(NodePool nodePool) {
        String clusterName = mainSettings.getClusterName();
        String instanceType = nodePool.getInstanceType();
        String name = isBlank(nodePool.getName()) ? "access-box" : nodePool.getName();
        String nodeName = String.format("%s-%s-%s-access", name, clusterName, instanceType);
        String image = isBlank(nodePool.getImage()) ? mainSettings.getImage() : nodePool.getImage();
        String[] additionalPackages = nodePool.getAdditionalPackages();
        String[] postCreateCommands = nodePool.getPostCreateCommands();
        String location = nodePool.getLocation();
        Server server = hetznerClient.findServer(nodeName);
        if (server != null) {
            System.out.println(ConsoleColors.GREEN + "Server " + nodeName + " already exists, skipping." + ConsoleColors.RESET);
            return server;
        }
        System.out.println("Creating access-box server " + nodeName + "...");
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
                "access-box",
                mainSettings.isDebug());
        System.out.println("...server " + nodeName + " created.");
        return server;
    }

}
