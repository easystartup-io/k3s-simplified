package io.easystartup.accessbox;

import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.configuration.AccessBoxConfig;
import io.easystartup.configuration.MainSettings;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.easystartup.utils.ServerUtils.waitForServerToComeUp;
import static io.easystartup.utils.Util.replaceTildaWithFullHomePath;
import static org.apache.commons.lang3.StringUtils.isBlank;

/*
 * @author indianBond
 */
public class CreateAccessBox {

    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;

    private final String configurationFilePath;

    private final SSH ssh;

    private Network hetznerCreatedNetwork;
    private SSHKey hetznerCreatedSSHKey;

    public CreateAccessBox(MainSettings mainSettings, String configurationFilePath) {
        this.mainSettings = mainSettings;
        this.hetznerClient = new HetznerClient(mainSettings.getHetznerToken());
        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());

        configurationFilePath = replaceTildaWithFullHomePath(configurationFilePath);

        this.configurationFilePath = configurationFilePath;


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

        // Todo: create firewall for access-box

        Server server = createServer();

        waitForServerToComeUp(server, ssh, mainSettings, false);

        installItems(server);

        ConsoleColors.println("\n=== Finished creating access box===\n", ConsoleColors.BLUE_BOLD);

        System.out.println("""
                The private and public keys have been copied to ~/.ssh/hetzner_rsa and ~/.ssh/hetzner_rsa.pub
                The cloud_config.yaml has also been copied to the root directory and the ssh_key paths replaced accordingly in the cluster_config.yaml
                And the hetzner token has been copied and set in the file itself
                And the kubeconfig path has changed to './kubeconfig' in the cluster_config.yaml
                """);

        System.out.println(getConnectToAccessBoxText(server));

        System.out.println("""
                And after connecting just run 
                k3s-simplified create --config cluster_config.yaml
                """);
    }

    private void installItems(Server server) {
        copyKeys(server);
        copyClusterConfig(server);
        installK3sSimplified(server);
        installKubectl(server);
    }

    private void installK3sSimplified(Server server) {
        System.out.println("Installing k3s-simplified");
        Map<String, Object> map = new HashMap<>();
        String command = TemplateUtil.renderTemplate(TemplateUtil.ACCESS_BOX_INSTALL_K3S_SIMPLIFIED, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent(), false);
        System.out.println(output);
        System.out.println("Finished installing k3s-simplified");
    }

    private void installKubectl(Server server) {
        System.out.println("Installing kubectl");
        Map<String, Object> map = new HashMap<>();
        // https://github.com/kubernetes/kubernetes/issues/7339
        map.put("kubeconfig_path_global_env", "KUBECONFIG=${HOME}/kubeconfig");
        String command = TemplateUtil.renderTemplate(TemplateUtil.ACCESS_BOX_INSTALL_KUBECTL, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent(), false);
        System.out.println(output);
        System.out.println("Finished installing kubectl");
    }

    /**
     * echo '{{ private_key }}' > '{{ private_key_path }}'
     * <p>
     * echo '{{ public_key }}' > '{{ public_key_path }}'
     * <p>
     * echo '{{ cluster_config }}' > '{{ cluster_config_path }}'
     */

    private void copyKeys(Server server) {
        System.out.println("Copying ssh keys to access box");
        Map<String, Object> map = new HashMap<>();
        map.put("private_key", getPrivateKeyFromFile());
        map.put("private_key_path", "~/.ssh/hetzner_rsa");
        map.put("public_key", getPublicKeyFromFile());
        map.put("public_key_path", "~/.ssh/hetzner_rsa.pub");
        String command = TemplateUtil.renderTemplate(TemplateUtil.ACCESS_BOX_COPY_SSH_KEYS, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent(), false);
        System.out.println(output);
        System.out.println("Finished copying ssh keys");
    }

    private void copyClusterConfig(Server server) {
        System.out.println("Copying cluster_config.yaml to access box");
        Map<String, Object> map = new HashMap<>();
        map.put("cluster_config", getModifiedClusterConfig());
        map.put("cluster_config_path", "~/cluster_config.yaml");

        String command = TemplateUtil.renderTemplate(TemplateUtil.ACCESS_BOX_COPY_CLUSTER_CONFIG, map);

        String output = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent(), false);
        System.out.println(output);
        System.out.println("Finished copying cluster_config.yaml");
    }

    private String getPublicKeyFromFile() {
        String publicSSHKeyPath = mainSettings.getPublicSSHKeyPath();
        try {
            return IOUtils.toString(new FileInputStream(publicSSHKeyPath));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private String getPrivateKeyFromFile() {
        String privateSSHKeyPath = mainSettings.getPrivateSSHKeyPath();
        try {
            return IOUtils.toString(new FileInputStream(privateSSHKeyPath));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private String getModifiedClusterConfig() {
        String path = replaceTildaWithFullHomePath(configurationFilePath);

        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(path)));
            content = replaceOrAddYamlValue(content, "kubeconfig_path", "\"./kubeconfig\"");
            content = replaceOrAddYamlValue(content, "public_ssh_key_path", "\"~/.ssh/hetzner_rsa.pub\"");
            content = replaceOrAddYamlValue(content, "private_ssh_key_path", "\"~/.ssh/hetzner_rsa\"");
            content = replaceOrAddYamlValue(content, "hetzner_token", "\"" + mainSettings.getHetznerToken() + "\"");
            content = replaceOrAddYamlValue(content, "use_ssh_agent", "false");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return content;
    }

    private String replaceOrAddYamlValue(String content, String key, String value) {
        // Check if the key is already present
        String patternString = "(?m)^(" + Pattern.quote(key) + ": ).*$";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            // If key is present, replace its value
            return matcher.replaceAll("$1" + value);
        } else {
            // If key is not present, append the key-value pair
            return content + "\n" + key + ": " + value;
        }
    }

    private String getConnectToAccessBoxText(Server server) {
        String privateSSHKeyPath = Util.replaceFullHomePathWithTilda(mainSettings.getPrivateSSHKeyPath());
        if (mainSettings.getSshPort() != 22) {
            return String.format("""
                    To connect to access box run this command,
                                    
                    ssh root@%s -p %s -i %s
                    """, server.getPublicNet().getIpv4().getIp(), mainSettings.getSshPort(), privateSSHKeyPath);
        } else {
            return String.format("""
                    To connect to access box run this command,
                                    
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