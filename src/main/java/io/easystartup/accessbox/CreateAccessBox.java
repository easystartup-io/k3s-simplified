package io.easystartup.accessbox;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.cluster.CreateCluster;
import io.easystartup.configuration.AccessBoxConfig;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.SSH;
import me.tomsdevsn.hetznercloud.objects.general.Location;
import me.tomsdevsn.hetznercloud.objects.general.Network;
import me.tomsdevsn.hetznercloud.objects.general.SSHKey;
import me.tomsdevsn.hetznercloud.objects.general.Server;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

/*
 * @author indianBond
 */
public class CreateAccessBox {

    private static final int MAX_INSTANCES_PER_PLACEMENT_GROUP = 10;
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private final Map<CreateCluster.ServerType, List<Server>> serverMap = new ConcurrentHashMap();

    private final SSH ssh;

    private Network hetznerCreatedNetwork;
    private SSHKey hetznerCreatedSSHKey;

    public CreateAccessBox(MainSettings mainSettings) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());

        List<String> errors = new ArrayList<>();
        validateAccessBoxSettings(errors);

        if (CollectionUtils.isNotEmpty(errors)) {
            System.out.println("INVALID ACCESS BOX SETTINGS");
            for (String error : errors) {
                System.out.println(error);
            }
            System.exit(1);
        }

    }

    private void validateAccessBoxSettings(List<String> errors) {
        if (mainSettings.getAccessBoxConfig() == null) {
            errors.add("No access box config present!");
            return;
        }
        AccessBoxConfig accessBoxConfig = mainSettings.getAccessBoxConfig();
        if (accessBoxConfig.getNode() == null) {
            errors.add("No access box node config present!");
            return;
        }
    }

    public void initialize() {
        ConsoleColors.println("\n=== Creating access box===\n", ConsoleColors.BLUE_BOLD);
        hetznerCreatedNetwork = findOrCreateNetwork();
        hetznerCreatedSSHKey = createSSH();

        Server server = createServer();

        ConsoleColors.println("\n=== Finished creating access box===\n", ConsoleColors.BLUE_BOLD);

        System.out.println(getConnectToAccessBoxText(server));
    }

    private String getConnectToAccessBoxText(Server server) {
        return String.format("""
                To connect to access box run this command,
                                
                ssh root@%s -p %s -i %s
                """, server.getPublicNet().getIpv4().getIp(), mainSettings.getSshPort(), mainSettings.getPrivateSSHKeyPath());
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