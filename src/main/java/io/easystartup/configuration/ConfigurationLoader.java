package io.easystartup.configuration;

/*
 * @author indianBond
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.cloud.hetzner.location.Location;
import io.easystartup.cloud.hetzner.network.Network;
import io.easystartup.utils.Releases;
import me.tomsdevsn.hetznercloud.objects.enums.Architecture;
import me.tomsdevsn.hetznercloud.objects.general.ServerType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.easystartup.utils.Util.replaceTildaWithFullHomePath;
import static org.apache.commons.lang3.StringUtils.isBlank;


public class ConfigurationLoader {
    private HetznerClient hetznerClient;
    private final List<String> errors = new ArrayList<>();
    private final Set<String> serverTypes;
    private final MainSettings settings; // Assuming MainSettings is a class that represents your configuration

    // Constructor
    public ConfigurationLoader(String configurationFilePath) {
        configurationFilePath = replaceTildaWithFullHomePath(configurationFilePath);

        String content;
        try {
            content = new String(Files.readAllBytes(Paths.get(configurationFilePath)));
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            this.settings = objectMapper.readValue(content, MainSettings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (isBlank(this.settings.getHetznerToken())) {
            this.settings.setHetznerToken(System.getenv("HCLOUD_TOKEN"));
        }

        replaceTildeWithUserHomeDirectory(settings);

        validateHetznerToken(errors);
        this.hetznerClient = new HetznerClient(settings.getHetznerToken());

        Map<String, ServerType> fullServerTypes = hetznerClient.getServerTypes().stream().collect(Collectors.toMap(ServerType::getName, Function.identity()));
        serverTypes = fullServerTypes.keySet();

        populateArchitecturesInWorkerNodePools(fullServerTypes);
        populateArchitecturesInNatGatewayNode(fullServerTypes);
    }

    private void populateArchitecturesInNatGatewayNode(Map<String, ServerType> fullServerTypes) {
        NatGatewayConfig natGatewayConfig = settings.getNatGatewayConfig();
        if (natGatewayConfig == null || natGatewayConfig.getNode() == null || natGatewayConfig.getNode().getInstanceCount() == 0) {
            return;
        }
        String instanceType = natGatewayConfig.getNode().getInstanceType();
        ServerType serverType = fullServerTypes.get(instanceType);
        if (serverType == null || serverType.getArchitecture() == null) {
            return;
        }
        NodePool.Architecture architecture = null;
        if (serverType.getArchitecture() == Architecture.arm) {
            architecture = NodePool.Architecture.arm64;
        } else if (serverType.getArchitecture() == Architecture.x86) {
            architecture = NodePool.Architecture.x86;
        }
        natGatewayConfig.getNode().setArchitecture(architecture);
    }

    private void populateArchitecturesInWorkerNodePools(Map<String, ServerType> fullServerTypes) {
        NodePool[] workerNodePools = settings.getWorkerNodePools();
        if (workerNodePools == null || workerNodePools.length == 0) {
            return;
        }
        for (NodePool workerNodePool : workerNodePools) {
            String instanceType = workerNodePool.getInstanceType();
            ServerType serverType = fullServerTypes.get(instanceType);
            if (serverType == null || serverType.getArchitecture() == null) {
                continue;
            }
            NodePool.Architecture architecture = null;
            if (serverType.getArchitecture() == Architecture.arm) {
                architecture = NodePool.Architecture.arm64;
            } else if (serverType.getArchitecture() == Architecture.x86) {
                architecture = NodePool.Architecture.x86;
            }
            workerNodePool.setArchitecture(architecture);
        }
    }

    public void validate() {
        validateCreateSettings(errors);
    }

    private void validateCreateSettings(List<String> errors) {
        validateClusterName(errors);
        validateKubeConfigPath(errors);
        validateK3sVersion(errors);
        validatePrivateSSHKey(errors);
        validatePublicSSHKey(errors);
        validateExistingNetworkName(errors);
        validateNetwork(errors, settings.getSSHAllowedNetworks(), "SSH");
        validateNetwork(errors, settings.getAPIAllowedNetworks(), "API");
        Location location = new Location(hetznerClient);
        Set<String> locations = location.getLocations();
        validateMastersPool(errors, locations);
        validateWorkersPool(errors, locations);
    }

    private void validateRelease(List<String> errors) {

    }

    /**
     * Hetzner gives error and it cant find servers if server names have upper case letters
     * */
    private void validateClusterName(List<String> errors) {
        String clusterName = settings.getClusterName();
        if (StringUtils.isBlank(clusterName)) {
            errors.add("cluster_name is an blank. (only lowercase letters, digits and dashes are allowed)");
        } else if (!Pattern.matches("^[a-z\\d-]+$", clusterName)) {
            errors.add("cluster_name is an invalid format (only lowercase letters, digits and dashes are allowed)");
        } else if (!Pattern.matches("^[a-z].*[a-z]$", clusterName)) {
            errors.add("Ensure that cluster_name starts and ends with a normal letter");
        }
    }

    private void validateMastersPool(List<String> errors, Set<String> locations) {
        validateNode(errors, locations, settings.getMastersPool());
        validateMasterInstanceCount(errors, settings.getMastersPool());
    }

    private void validateWorkersPool(List<String> errors, Set<String> locations) {
        if (settings.getScheduleWorkloadsOnMasters()) {
            return;
        }
        validatePoolName(errors, settings.getWorkerNodePools());
        validateUniqueWorkNames(errors, settings.getWorkerNodePools());
        for (NodePool workerNodePool : settings.getWorkerNodePools()) {
            validateNode(errors, locations, workerNodePool);
        }
    }

    private void validateNode(List<String> errors, Set<String> locations, NodePool nodePool) {
        validateInstanceType(errors, nodePool);
        validateLocation(errors, locations, nodePool);
        validateLabels(errors, nodePool);
        validateTaints(errors, nodePool);
        validateAutoScaling(errors, nodePool);
    }

    private void validateAutoScaling(List<String> errors, NodePool nodePool) {
        AutoScaling autoScaling = nodePool.getAutoScaling();
        if (autoScaling == null || !autoScaling.isEnabled()) {
            return;
        }
        if (autoScaling.getMinInstances() == null) {
            errors.add(getPoolNameOrMaster(nodePool) + " please disable autoscaling or mention min_instances");
        }
        if (autoScaling.getMaxInstances() == null) {
            errors.add(getPoolNameOrMaster(nodePool) + " please disable autoscaling or mention max_instances");
        }

        if (autoScaling.getMaxInstances() != null && autoScaling.getMinInstances() != null && autoScaling.getMaxInstances() < autoScaling.getMinInstances()) {
            errors.add(getPoolNameOrMaster(nodePool) + " please ensure min instances are less than or equal to max instances");
        }
    }

    private void validateTaints(List<String> errors, NodePool nodePool) {
        if (nodePool.getTaints() == null || nodePool.getTaints().length == 0) {
            return;
        }
        for (KeyValuePair taint : nodePool.getTaints()) {
            if (StringUtils.isBlank(taint.getKey()) || StringUtils.isBlank(taint.getValue())) {
                errors.add(getPoolNameOrMaster(nodePool) + " has invalid taints");
                break;
            }
        }
    }

    private void validateLabels(List<String> errors, NodePool nodePool) {
        if (nodePool.getLabels() == null || nodePool.getLabels().length == 0) {
            return;
        }
        for (KeyValuePair label : nodePool.getLabels()) {
            if (StringUtils.isBlank(label.getKey()) || StringUtils.isBlank(label.getValue())) {
                errors.add(getPoolNameOrMaster(nodePool) + " has invalid labels");
                break;
            }
        }
    }

    private void validateMasterInstanceCount(List<String> errors, NodePool nodePool) {
        long instanceCount = nodePool.getInstanceCount();
        if (instanceCount > 0 && (instanceCount == 1 || isOdd(instanceCount))) {
            return;
        }
        errors.add("Masters count must equal to 1 for non-HA clusters or an odd number (recommended 3) for an HA cluster");
    }

    private void validateLocation(List<String> errors, Set<String> locations, NodePool nodePool) {
        boolean contains = locations.contains(nodePool.getLocation());
        if (!contains) {
            errors.add(getPoolNameOrMaster(nodePool) + " node pool has an invalid location");
        }
    }

    private void validateInstanceType(List<String> errors, NodePool nodePool) {
        boolean contains = serverTypes.contains(nodePool.getInstanceType());
        if (!contains) {
            errors.add(getPoolNameOrMaster(nodePool) + " node pool has an invalid instance type");
        }
    }

    private String getPoolNameOrMaster(NodePool nodePool) {
        return isBlank(nodePool.getName()) ? "master" : nodePool.getName();
    }

    private void validateUniqueWorkNames(List<String> errors, NodePool[] workerNodePools) {
        Set<String> names = new HashSet<>();
        for (NodePool pool : workerNodePools) {
            if (names.contains(pool.getName())) {
                errors.add(pool.getName() + " worker pool already exists! Each worker node pool must have a unique name!");
            }
            names.add(pool.getName());
        }
    }

    private void validatePoolName(List<String> errors, NodePool[] workerPools) {
        if (workerPools == null) {
            return;
        }
        for (NodePool workerPool : workerPools) {
            if (StringUtils.isBlank(workerPool.getName())) {
                errors.add("Worker pool cannot have a blank name!");
                continue;
            }
            if (validPoolName(workerPool.getName())) {
                continue;
            }
            errors.add(workerPool.getName() + " worker pool has an invalid name!");
        }
    }

    private boolean validPoolName(String name) {
        Pattern pattern = Pattern.compile("^[A-Za-z0-9\\-_]+$");
        return pattern.matcher(name).matches();
    }

    private void validateNetwork(List<String> errors, String[] networks, String networkType) {
        if (networks == null || networks.length < 1) {
            errors.add(networkType + " allowed networks are requird");
            return;
        }
        for (String network : networks) {
            validateCidrNetwork(network);
        }
        validateCurrentIPMustBeIncludedInAtLeastOneNetwork(errors, networks);
    }

    private void validateCurrentIPMustBeIncludedInAtLeastOneNetwork(List<String> errors, String[] networks) {
        String currentIP = getCurrentIP();
        if (currentIP == null) {
            errors.add("Unable to determine your current IP (necessary to validate allowed networks)");
            return;
        }

        boolean included = false;
        for (String cidr : networks) {
            if (isIPInCIDR(currentIP, cidr)) {
                included = true;
                break;
            }
        }

        if (!included) {
            errors.add("Your current IP " + currentIP + " must belong to at least one of the allowed networks");
            return;
        }
    }

    private String getCurrentIP() {
        try {
            URL url = new URL("http://whatismyip.akamai.com");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                return in.readLine();
            }
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }

    private boolean isIPInCIDR(String ip, String cidr) {
        SubnetUtils utils = new SubnetUtils(cidr);
        utils.setInclusiveHostCount(true);
        return utils.getInfo().isInRange(ip);
    }


    private void validateCidrNetwork(String cidr) {
        try {
            SubnetUtils utils = new SubnetUtils(cidr);
            utils.getInfo(); // This line will throw an IllegalArgumentException if the CIDR is invalid
        } catch (IllegalArgumentException e) {
            errors.add("Allowed network " + cidr + " is not a valid network in CIDR notation");
            return;
        }
    }

    private void validateExistingNetworkName(List<String> errors) {
        if (isBlank(settings.getExistingNetworkName())) {
            return;
        }
        Network network = new Network(hetznerClient);
        me.tomsdevsn.hetznercloud.objects.general.Network nw = network.find(settings.getExistingNetworkName());
        if (nw == null) {
            String format = String.format("You have specified that you want to use the existing network named '%s' but this network doesn't exist", settings.getExistingNetworkName());
            errors.add(format);
            return;
        }
    }

    private void validatePublicSSHKey(List<String> errors) {
        if (isBlank(settings.getPublicSSHKeyPath())) {
            errors.add("public_ssh_key_path cannot be blank");
            return;
        }
        validateFile(errors, "public_ssh_key_path", settings.getPublicSSHKeyPath(), true);
    }

    private void validatePrivateSSHKey(List<String> errors) {
        if (isBlank(settings.getPrivateSSHKeyPath())) {
            errors.add("private_ssh_key_path cannot be blank");
            return;
        }
        validateFile(errors, "private_ssh_key_path", settings.getPrivateSSHKeyPath(), true);
    }

    private void validateK3sVersion(List<String> errors) {
        if (isBlank(settings.getK3SVersion())) {
            errors.add("k3s_version cannot be blank");
            return;
        }

        List<String> availableReleases = new Releases().availableReleases();
        String k3SVersion = settings.getK3SVersion();
        if (!availableReleases.contains(k3SVersion)){
            errors.add("K3s version is not valid, run `k3s-simplified releases` to see available versions");
        }
    }

    private void validateKubeConfigPath(List<String> errors) {
        String kubeconfigPath = this.settings.getKubeconfigPath();
        if (isBlank(kubeconfigPath)) {
            errors.add("kubeconfig_path is required");
            return;
        }
        validateFile(errors, "kubeconfig_path", kubeconfigPath, false);
    }

    private void validateFile(List<String> errors, String key, String path, boolean checkIfReadable) {
        Path filePath = Paths.get(path);

        // Check if the file exists
        boolean exists = Files.exists(filePath);

        // Check if the path is a directory
        boolean isDirectory = Files.isDirectory(filePath);

        if (isDirectory && exists) {
            errors.add(key + " exists and is a directory, we need at a file path");
            return;
        } else if (isDirectory) {
            errors.add(key + " cannot point to a directory, please specify a file path");
            return;
        }

        if (checkIfReadable & !exists) {
            errors.add(path + " - file does not exist! For " + key);
            return;
        }
        if (checkIfReadable && !Files.isReadable(filePath)) {
            errors.add(key + " is not readable! Please check the permissions");
            return;
        }
    }

    private void validateHetznerToken(List<String> errors) {
        if (isBlank(settings.getHetznerToken())) {
            errors.add("Hetzner API token is missing, please set it in the configuration file or in the environment variable HCLOUD_TOKEN");
            return;
        }
    }

    public boolean isOdd(long number) {
        return number % 2 != 0;
    }

    public List<String> getErrors() {
        return errors;
    }

    public MainSettings getSettings() {
        return this.settings;
    }

    private void replaceTildeWithUserHomeDirectory(MainSettings settings) {
        settings.setKubeconfigPath(replaceTildaWithFullHomePath(settings.getKubeconfigPath()));
        settings.setPrivateSSHKeyPath(replaceTildaWithFullHomePath(settings.getPrivateSSHKeyPath()));
        settings.setPublicSSHKeyPath(replaceTildaWithFullHomePath(settings.getPublicSSHKeyPath()));
    }

    public void validateUpgradeSettings(String k3SVersion, String newK3sVersion) {
        List<String> availableReleases = new Releases().availableReleases();
        if (!availableReleases.contains(k3SVersion)){
            errors.add("K3s version is not valid in your config file, run `k3s-simplified releases` to see available versions");
        }
        if (!availableReleases.contains(newK3sVersion)){
            errors.add("New K3s version is not valid, run `k3s-simplified releases` to see available versions");
        }
        int newK3sIndex = availableReleases.indexOf(newK3sVersion);
        int existingK3sIndex = availableReleases.indexOf(k3SVersion);
        if (existingK3sIndex >= newK3sIndex){
            errors.add("New K3s version should be more recent than existing one, run `k3s-simplified releases` to see available versions");
        }
    }
}
