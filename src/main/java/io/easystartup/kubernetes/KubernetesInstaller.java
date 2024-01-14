package io.easystartup.kubernetes;

import io.easystartup.cluster.CreateCluster;
import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.configuration.AutoScaling;
import io.easystartup.configuration.KeyValuePair;
import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.*;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.easystartup.cloud.hetzner.HetznerClient.cloudInit;
import static io.easystartup.utils.Util.sleep;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/*
 * @author indianBond
 */
public class KubernetesInstaller {
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private final LoadBalancer loadBalancer;
    private final Map<CreateCluster.ServerType, List<Server>> servers;

    private final SSH ssh;

    private final Network network;
    private final Firewall firewall;
    private final SSHKey sshKey;

    private final boolean debug;

    public KubernetesInstaller(MainSettings mainSettings, HetznerClient hetznerClient, LoadBalancer loadBalancer, Map<CreateCluster.ServerType, List<Server>> servers, Network network, Firewall firewall, SSHKey sshKey) {
        this.mainSettings = mainSettings;
        this.debug = mainSettings.isDebug();
        this.hetznerClient = hetznerClient;
        this.loadBalancer = loadBalancer;
        this.servers = servers;
        this.network = network;
        this.firewall = firewall;
        this.sshKey = sshKey;

        this.ssh = new SSH(mainSettings.getPrivateSSHKeyPath(), mainSettings.getPublicSSHKeyPath());
    }


    public void startInstallation() {
        System.out.println(ConsoleColors.BLUE_BOLD + "\n=== Setting up Kubernetes ===\n" + ConsoleColors.RESET);

        Util.checkKubectl();
        List<Server> serverList = servers.get(CreateCluster.ServerType.MASTER);
        setUpFirstMaster(serverList.get(0));

        waitForControlPlaneFirstNodeToBeReady(serverList.get(0));

        setUpOtherMasters(serverList);

        String k3sToken = getK3sTokenByFallingBackToDifferentMasters().getLeft();

        setUpWorkers(servers.get(CreateCluster.ServerType.WORKER), k3sToken);

        System.out.println("\n=== Deploying Hetzner drivers ===\n");


        addLabelsAndTaintsToMasters();
        addLabelsAndTaintsToWorkers();

        createHetznerCloudSecret();
        deployCloudControllerManager();
        deployCsiDriver();
        deploySystemUpgradeController();
        deployClusterAutoscaler(k3sToken);
    }

    private void waitForControlPlaneFirstNodeToBeReady(Server server) {
        System.out.println("Validating that kubectl is working and able to query for nodes");
        long tic = System.currentTimeMillis();
        while (true) {
            String command = "kubectl get nodes";
            ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
            if (result.isSuccess() && result.getOutput().contains(server.getName())) {
                break;
            }
            Util.sleep(2_000L);
            long tac = System.currentTimeMillis();
            if ((tac - tic) > TimeUnit.MINUTES.toMillis(4)) {
                System.out.println("Its taking too long to connect, please check that your control plane is  accessible from here and please try again");
                if (StringUtils.isNotBlank(mainSettings.getAPIServerHostname())) {
                    System.out.println("Also ensure that your DNS is pointed properly for " + mainSettings.getAPIServerHostname());
                }
                System.exit(1);
            }
        }
    }

    private void setUpFirstMaster(Server firstMaster) {
        System.out.println("Deploying k3s to first master " + firstMaster.getName() + "...");

        Triple<String, Server, Integer> tokenVsServerVsServerIndex = getK3sTokenByFallingBackToDifferentMasters();
        Integer masterServerIndex = tokenVsServerVsServerIndex.getRight();
        String k3sTokenByFallingBackToDifferentMasters = tokenVsServerVsServerIndex.getLeft();
        boolean clusterDoingInit = true;
        if (StringUtils.isNotBlank(k3sTokenByFallingBackToDifferentMasters) && masterServerIndex != null && masterServerIndex != 0) {
            // Means that first master was deleted and it needs to join back the cluster now, there is some other current etcd leader
            System.out.println(ConsoleColors.RED + "First master was deleted but other masters are working, so rejoining the first master back to existing cluster" + ConsoleColors.RESET);
            clusterDoingInit = false;
        }

        String command = masterInstallScript(firstMaster, clusterDoingInit, k3sTokenByFallingBackToDifferentMasters);
        printDebug(command);
        String output = ssh.ssh(firstMaster, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());

        System.out.println(output);
        System.out.println("Waiting for the control plane to be ready...");

        sleep(10_000); // Sleep for 10 seconds

        saveKubeconfig(firstMaster);

        System.out.println("...k3s has been deployed to first master " + firstMaster.getName() + " and the control plane is up.");
    }

    public void saveKubeconfig(Server firstMaster) {
        // Command to get the kubeconfig content from the master server
        String command = "cat /etc/rancher/k3s/k3s.yaml";

        // Execute the command via SSH and store the output (kubeconfig content)
        String kubeconfigContent = ssh.ssh(firstMaster, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());

        String apiServerHostname = isNotBlank(mainSettings.getAPIServerHostname()) ? mainSettings.getAPIServerHostname() : getApiServerIpAddress();
        // Replace the server address placeholder with the actual API server address
        kubeconfigContent = kubeconfigContent.replace("127.0.0.1", apiServerHostname);
        kubeconfigContent = kubeconfigContent.replace("default", mainSettings.getClusterName());

        // Define the local path where the kubeconfig file will be saved
        String kubeconfigPath = mainSettings.getKubeconfigPath();

        // Save the kubeconfig content to a file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(kubeconfigPath))) {
            writer.write(kubeconfigContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Set file permissions (optional, based on your security requirements)
        setFilePermissions(kubeconfigPath, "rw-------");

        System.out.println("Kubeconfig saved to " + kubeconfigPath);
    }

    private void setFilePermissions(String path, String permissions) {
        try {
            Path filePath = Paths.get(path);
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);

            Files.setPosixFilePermissions(filePath, perms);
            System.out.println("Set permissions " + permissions + " on " + path);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to set file permissions for " + path);
        }
    }
    private void setUpOtherMasters(List<Server> masters) {
        if (masters.size() < 2) {
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(masters.size() - 1);
        List<Future<?>> futures = new ArrayList<>();
        String k3sTokenByFallingBackToDifferentMasters = getK3sTokenByFallingBackToDifferentMasters().getLeft();
        for (Server master : masters.subList(1, masters.size())) {
            futures.add(executor.submit(() -> deployK3sToOtherMasters(master, k3sTokenByFallingBackToDifferentMasters)));
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

    private void setUpWorkers(List<Server> workers, String k3sToken) {
        if (CollectionUtils.isEmpty(workers)){
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());
        List<Future<?>> futures = new ArrayList<>();

        for (Server worker : workers) {
            futures.add(executor.submit(() -> deployK3sToWorker(worker, k3sToken)));
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

    private void deployK3sToOtherMasters(Server master, String k3sTokenByFallingBackToDifferentMasters) {
        System.out.println("Deploying k3s to master " + master.getName() + "...");
        String command = masterInstallScript(master, false, k3sTokenByFallingBackToDifferentMasters);
        printDebug(master.getName() + "\n" + command);
        String ssh1 = ssh.ssh(master, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());
        System.out.println(master.getName() + "\n" + ssh1);
        System.out.println("...k3s has been deployed to master " + master.getName() + ".");
    }

    private void deployK3sToWorker(Server worker, String k3sToken) {
        System.out.println("Deploying k3s to worker " + worker.getName() + "...");
        ssh.ssh(worker, mainSettings.getSshPort(), workerInstallScript(k3sToken), mainSettings.isUseSSHAgent());
        System.out.println("...k3s has been deployed to worker " + worker.getName() + ".");
    }

    private void createHetznerCloudSecret() {
        System.out.println("\nCreating secret for Hetzner Cloud token...");

        Map<String, Object> cloudSecretData = new HashMap<>();
        cloudSecretData.put("network", isNotBlank(mainSettings.getExistingNetworkName()) ? mainSettings.getExistingNetworkName() : mainSettings.getClusterName());
        cloudSecretData.put("token", mainSettings.getHetznerToken());
        String secretManifest = TemplateUtil.renderTemplate(TemplateUtil.HETZNER_CLOUD_SECRET_MANIFEST, cloudSecretData);
        String command = "kubectl apply -f - <<EOF\n" + secretManifest + "\nEOF";

        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to create Hetzner Cloud secret:");
            System.out.println(result.getOutput());
            System.out.println(result.getStatus());
            throw new RuntimeException("Failed to create Hetzner Cloud secret");
        }

        System.out.println("...secret created.");
    }

    private void deployCloudControllerManager() {
        System.out.println("\nDeploying Hetzner Cloud Controller Manager...");

        try {
            String ccmManifest = downloadCCMManifest(mainSettings.getCloudControllerManagerManifestURL());
            ccmManifest = ccmManifest.replaceAll("--cluster-cidr=[^\"]+", "--cluster-cidr=" + mainSettings.getClusterCIDR());

            String ccmManifestPath = "/tmp/ccm_manifest.yaml";
            Files.writeString(Paths.get(ccmManifestPath), ccmManifest);

            String command = "kubectl apply -f " + ccmManifestPath;
            ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

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

        String command = "kubectl apply -f " + mainSettings.getCSIDriverManifestURL();
        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to deploy CSI Driver:");
            System.out.println(result);
            throw new RuntimeException("Failed to deploy CSI Driver");
        }

        System.out.println("...CSI Driver deployed");
    }

    private void deploySystemUpgradeController() {
        System.out.println("\nDeploying k3s System Upgrade Controller...");

        String command = "kubectl apply -f " + mainSettings.getSystemUpgradeControllerManifestURL();
        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to deploy k3s System Upgrade Controller:");
            System.out.println(result);
            throw new RuntimeException("Failed to deploy k3s System Upgrade Controller");
        }

        System.out.println("...k3s System Upgrade Controller deployed.");
    }

    private void deployClusterAutoscaler(String k3sToken) {
        System.out.println("\nDeploying Cluster Autoscaler...");
        List<Server> serverList = servers.get(CreateCluster.ServerType.MASTER);
        Server firstMaster = serverList.get(0);

        NodePool[] workerNodePools = mainSettings.getWorkerNodePools();
        if (workerNodePools == null || workerNodePools.length == 0) {
            return;
        }
        Set<NodePool> autoScalingWorkerNodePools = Arrays.stream(workerNodePools).filter(val -> val.getAutoScaling() != null && val.getAutoScaling().isEnabled()).collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(autoScalingWorkerNodePools)) {
            return;
        }

        String nodePoolArgs = autoScalingWorkerNodePools.stream()
                .map(pool -> {
                    AutoScaling autoScaling = pool.getAutoScaling();
                    return "- --nodes=" + autoScaling.getMinInstances() + ":" + autoScaling.getMaxInstances() +
                            ":" + pool.getInstanceType().toUpperCase() + ":" + pool.getLocation().toUpperCase() +
                            ":" + pool.getName();
                })
                .collect(Collectors.joining("\n            "));

        String k3sJoinScript = "|\\n    " + workerInstallScript(k3sToken).replace("\n", "\\n    ");

        // Assuming cloudInit method is implemented to create cloud-init for Hetzner Server
        String cloudInit = cloudInit(
                mainSettings.getSshPort(),
                mainSettings.getSnapshotOs(),
                getAdditionalPackages(),
                getAdditionalPostCreateCommands(),
                List.of(k3sJoinScript),
                debug);

        String certificatePath = checkCertificatePath(firstMaster);
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("node_pool_args", nodePoolArgs);
        dataModel.put("cloud_init", Base64.getEncoder().encodeToString(cloudInit.getBytes()));
        dataModel.put("image", isBlank(mainSettings.getAutoScalingImage()) ? mainSettings.getImage() : mainSettings.getAutoScalingImage());
        dataModel.put("firewall_name", mainSettings.getClusterName());
        dataModel.put("ssh_key_name", mainSettings.getClusterName());
        dataModel.put("network_name", mainSettings.getClusterName());
        dataModel.put("certificate_path", certificatePath);

        String clusterAutoscalerManifest = TemplateUtil.renderTemplate(TemplateUtil.CLUSTER_AUTOSCALER_MANIFEST, dataModel);

        String clusterAutoscalerManifestPath = "/tmp/cluster_autoscaler_manifest.yaml";
        try {
            Files.writeString(Paths.get(clusterAutoscalerManifestPath), clusterAutoscalerManifest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String command = "kubectl apply -f " + clusterAutoscalerManifestPath;
        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to deploy Cluster Autoscaler:");
            System.out.println(result.getOutput());
            System.exit(1);
        }

        System.out.println("...Cluster Autoscaler deployed.");
    }

    private List<String> getAdditionalPostCreateCommands() {
        if (mainSettings.getPostCreateCommands() == null || mainSettings.getPostCreateCommands().length == 0) {
            return List.of();
        }
        return Arrays.stream(mainSettings.getPostCreateCommands()).toList();
    }

    private List<String> getAdditionalPackages() {
        if (mainSettings.getAdditionalPackages() == null || mainSettings.getAdditionalPackages().length == 0) {
            return List.of();
        }
        return Arrays.stream(mainSettings.getAdditionalPackages()).toList();
    }

    private String checkCertificatePath(Server firstMaster) {
        String checkCommand = "[ -f /etc/ssl/certs/ca-certificates.crt ] && echo 1 || echo 2";
        String result = ssh.ssh(firstMaster, mainSettings.getSshPort(), checkCommand, mainSettings.isUseSSHAgent());

        if ("1".equals(result.trim())) {
            return "/etc/ssl/certs/ca-certificates.crt";
        } else {
            return "/etc/ssl/certs/ca-bundle.crt";
        }
    }

    public void addLabelsAndTaintsToMasters() {
        List<Server> masters = servers.get(CreateCluster.ServerType.MASTER);
        KeyValuePair[] labels = mainSettings.getMastersPool().getLabels();
        KeyValuePair[] taints = mainSettings.getMastersPool().getTaints();

        //Todo: Dont make multiple requests, single request should handle for all masters
        for (Server master : masters) {
            applyLabels(master.getName(), labels);
            applyTaints(master.getName(), taints);
        }
    }

    private void applyLabels(String nodeName, KeyValuePair[] labels) {
        if (labels == null || labels.length == 0) {
            return;
        }
        String labelString = Arrays.stream(labels)
                .map(label -> label.getKey() + "=" + label.getValue())
                .collect(Collectors.joining(","));
        if (!labelString.isEmpty()) {
            System.out.println("Adding label to " + nodeName);
            String command = "kubectl label nodes " + nodeName + " " + labelString + " --overwrite";
            ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
        }
    }

    private void applyTaints(String nodeName, KeyValuePair[] taints) {
        if (taints == null || taints.length == 0) {
            return;
        }
        Arrays.stream(taints).forEach(taint -> {
            System.out.println("Adding taint to " + nodeName);
            String taintString = taint.getKey() + "=" + taint.getValue();
            String command = "kubectl taint nodes " + nodeName + " " + taintString + " --overwrite";
            ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
        });
    }

    private void addLabelsAndTaintsToWorkers() {
        System.out.println("\nAdding labels and taints to workers...");
        List<Server> workers = servers.get(CreateCluster.ServerType.WORKER);

        for (NodePool nodePool : mainSettings.getWorkerNodePools()) {
            String instanceType = nodePool.getInstanceType();
            String nodePoolRegex = mainSettings.getClusterName() + "-" + instanceType + "-pool-" + nodePool.getName() + "-worker";
            Pattern pattern = Pattern.compile(nodePoolRegex);

            List<Server> nodes = workers.stream()
                    .filter(worker -> pattern.matcher(worker.getName()).matches())
                    .collect(Collectors.toList());

            String nodeNames = nodes.stream().map(Server::getName).collect(Collectors.joining(" "));
            addLabelsToWorkers(nodePool, nodeNames);
            addTaintsToWorkers(nodePool, nodeNames);
        }

        System.out.println("...done.");
    }

    private void addTaintsToWorkers(NodePool nodePool, String nodeNames) {
        if (nodePool.getTaints() == null || nodePool.getTaints().length == 0) {
            return;
        }
        String allMarks = Arrays.stream(nodePool.getTaints())
                .map(taint -> taint.getKey() + "=" + taint.getValue())
                .collect(Collectors.joining(" "));

        String command = "kubectl taint --overwrite nodes " + nodeNames + " " + allMarks;
        ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
    }

    private void addLabelsToWorkers(NodePool nodePool, String nodeNames) {
        if (nodePool.getLabels() == null || nodePool.getLabels().length == 0) {
            return;
        }
        String allMarks = Arrays.stream(nodePool.getLabels())
                .map(label -> label.getKey() + "=" + label.getValue())
                .collect(Collectors.joining(" "));

        String command = "kubectl label --overwrite nodes " + nodeNames + " " + allMarks;
        ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());
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
        List<Server> masters = servers.get(CreateCluster.ServerType.MASTER);
        if (masters.size() > 1 && loadBalancer != null) {
            sans.add("--tls-san=" + loadBalancer.getPrivateNet().get(0).getIp());
        }

        for (Server master : masters) {
            String masterPrivateIp = master.getPrivateNet().get(0).getIp();
            sans.add("--tls-san=" + masterPrivateIp);
        }

        return String.join(" ", sans);
    }

    private String masterInstallScript(Server master, boolean clusterDoingInit, String k3sTokenByFallingBackToDifferentMasters) {
        String server = clusterDoingInit ? " --cluster-init " : " --server https://" + getApiServerIpAddress() + ":6443 ";
        String flannelBackend = findFlannelBackend();
        String extraArgs = computeExtraArgs();
        String taint = mainSettings.isScheduleWorkloadsOnMasters() ? " " : " --node-taint CriticalAddonsOnly=true:NoExecute ";

        Map<String, Object> map = new HashMap<>();
        map.put("cluster_name", mainSettings.getClusterName());
        map.put("k3s_version", mainSettings.getK3SVersion());
        map.put("k3s_token", k3sTokenByFallingBackToDifferentMasters);
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
        return TemplateUtil.renderTemplate(TemplateUtil.MASTER_INSTALL_SCRIPT, map);
    }

    /**
     * When upgrading or deleted first master, need to join first master to existing cluster
     * hence falling back to other masters to get token, then we can setup first master to join the cluster
     */
    private Triple<String, Server, Integer> getK3sTokenByFallingBackToDifferentMasters() {
        List<Server> serverList = servers.get(CreateCluster.ServerType.MASTER);
        String token = null;
        int masterServerIndex = 0;
        Server masterServerWithToken = null;
        for (Server server : serverList) {
            String command = "cat /var/lib/rancher/k3s/server/node-token";
            token = ssh.ssh(server, mainSettings.getSshPort(), command, mainSettings.isUseSSHAgent());
            if (StringUtils.isNotBlank(token)) {
                masterServerWithToken = server;
                break;
            }
            masterServerIndex++;
        }

        if (token == null || token.isEmpty()) {
            token = new BigInteger(130, new SecureRandom()).toString(32); // Random token generation
        }

        return Triple.of(token, masterServerWithToken, masterServerIndex);
    }

    private String getApiServerIpAddress() {
        List<Server> masterServer = servers.get(CreateCluster.ServerType.MASTER);
        if (masterServer.size() > 1 && loadBalancer != null) {
            if (mainSettings.isPrivateApiLoadBalancer()
                    && isNotEmpty(loadBalancer.getPrivateNet())
                    && isNotBlank(loadBalancer.getPrivateNet().get(0).getIp())) {
                return loadBalancer.getPrivateNet().get(0).getIp();
            } else if (Boolean.TRUE.equals(loadBalancer.getPublicEnabled()) && StringUtils.isNotBlank(loadBalancer.getPublicIpv4())) {
                return loadBalancer.getPublicIpv4();
            }
        }
        return getHostIp(masterServer.get(0));
    }

    private String getHostIp(Server server) {
        PublicNet publicNet = server.getPublicNet();
        if (publicNet != null && publicNet.getIpv4() != null && isNotBlank(publicNet.getIpv4().getIp())) {
            return publicNet.getIpv4().getIp();
        }
        List<PrivateNet> privateNet = server.getPrivateNet();
        if (isNotEmpty(privateNet)) {
            return privateNet.get(0).getIp();
        }
        return null;
    }

    private String workerInstallScript(String k3sToken) {

        List<Server> serverList = servers.get(CreateCluster.ServerType.MASTER);
        Server firstMaster = serverList.get(0);
        String firstMasterPrivateIp = firstMaster.getPrivateNet().get(0).getIp();

        Map<String, Object> data = new HashMap<>();
        data.put("cluster_name", mainSettings.getClusterName());
        data.put("k3s_token", k3sToken);
        data.put("k3s_version", mainSettings.getK3SVersion());
        data.put("first_master_private_ip_address", firstMasterPrivateIp);
        data.put("private_network_test_ip", getPrivateNetworkTestIp(mainSettings.getPrivateNetworkSubnet()));
        return TemplateUtil.renderTemplate(TemplateUtil.WORKER_INSTALL_SCRIPT, data);
    }

    private String findFlannelBackend() {
        if (!mainSettings.isEnableEncryption()) {
            return " ";
        }

        // We support everything beyond 1.23 so flannel latest is fine
        return " --flannel-backend=wireguard-native ";
    }

    /**
     * Extracts the first three octets from a subnet string and appends ".1" to it.
     *
     * @param privateNetworkSubnet The subnet string, e.g., "10.0.0.0/16".
     * @return The modified IP address string.
     */
    public static String getPrivateNetworkTestIp(String privateNetworkSubnet) {
        String[] parts = privateNetworkSubnet.split("/")[0].split("\\.");
        String joinedParts = String.join(".", parts[0], parts[1], parts[2]);
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
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(arg -> " --" + component + "-arg=\"" + arg + "\" ")
                .collect(Collectors.joining());
    }

    private String downloadCCMManifest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        } finally {
            con.disconnect();
        }
    }


    private void printDebug(String command) {
        if (!debug) {
            return;
        }
        System.out.println(ConsoleColors.YELLOW + command + ConsoleColors.RESET);
    }
}
