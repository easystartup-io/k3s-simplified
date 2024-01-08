package io.easystartup.configuration;

/*
 * @author indianBond
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.easystartup.cloud.hetzner.HetznerClient;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;


public class ConfigurationLoader {
    private HetznerClient hetznerClient;
    private List<String> errors = new ArrayList<>();
    private MainSettings settings; // Assuming MainSettings is a class that represents your configuration

    // Constructor
    public ConfigurationLoader(String configurationFilePath) {
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
        validateHetznerToken(errors);
        this.hetznerClient = new HetznerClient(settings.getHetznerToken());
    }


    public void validate() {
        validateCreateSettings(errors);
    }

    private void validateCreateSettings(List<String> errors) {
        validateKubeConfigPath(errors);
        validateK3sVersion(errors);
        validatePrivateSSHKey(errors);
        validatePublicSSHKey(errors);
        validateExistingNetworkName(errors);
        validateNetwork(errors, settings.getSSHAllowedNetworks(), "SSH");
        validateNetwork(errors, settings.getAPIAllowedNetworks(), "API");
        Set<String> serverTypes = hetznerClient.getServerTypes();
        Set<String> locations = hetznerClient.getLocations();
        validateMastersPool(errors, serverTypes, locations);
        validateWorkersPool(errors, serverTypes, locations);
    }

    private void validateMastersPool(List<String> errors, Set<String> serverTypes, Set<String> locations) {
        validateNode(errors, serverTypes, locations, settings.getMastersPool());
        validateMasterInstanceCount(errors, settings.getMastersPool());
    }

    private void validateWorkersPool(List<String> errors, Set<String> serverTypes, Set<String> locations) {
        if (settings.getScheduleWorkloadsOnMasters()) {
            return;
        }
        validatePoolName(errors, settings.getWorkerNodePools());
        validateUniqueWorkNames(errors, settings.getWorkerNodePools());
        for (NodePool workerNodePool : settings.getWorkerNodePools()) {
            validateNode(errors, serverTypes, locations, workerNodePool);
        }
    }

    private void validateNode(List<String> errors, Set<String> serverTypes, Set<String> locations, NodePool nodePool) {
        validateInstanceType(errors, serverTypes, nodePool);
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

    private static void validateInstanceType(List<String> errors, Set<String> serverTypes, NodePool nodePool) {
        boolean contains = serverTypes.contains(nodePool.getInstanceType());
        if (!contains) {
            errors.add(getPoolNameOrMaster(nodePool) + " node pool has an invalid instance type");
        }
    }

    private static String getPoolNameOrMaster(NodePool nodePool) {
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
        Pattern pattern = Pattern.compile("\\A([A-Za-z0-9\\-_]+)\\Z");
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
        Set<String> networks = this.hetznerClient.getNetworks();
        if (!networks.contains(settings.getExistingNetworkName())) {
            String format = String.format("You have specified that you want to use the existing network named '%s' but this network doesn't exist", settings.getExistingNetworkName());
            errors.add(format);
            return;
        }
    }

    private void validatePublicSSHKey(List<String> errors) {
        if (isBlank(settings.getHetznerToken())) {
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

    public static boolean isOdd(long number) {
        return number % 2 != 0;
    }

    public List<String> getErrors() {
        return errors;
    }

}
