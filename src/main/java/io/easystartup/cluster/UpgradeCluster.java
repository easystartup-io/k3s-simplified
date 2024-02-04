package io.easystartup.cluster;

import io.easystartup.configuration.MainSettings;
import io.easystartup.configuration.NodePool;
import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.ShellUtil;
import io.easystartup.utils.TemplateUtil;
import io.easystartup.utils.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/*
 * @author indianBond
 */
public class UpgradeCluster {

    private final MainSettings mainSettings;

    private final String newK3sVersion;
    private final String configFilePath;

    public UpgradeCluster(MainSettings mainSettings, String newK3sVersion, String configFilePath) {
        this.mainSettings = mainSettings;
        this.newK3sVersion = newK3sVersion;
        this.configFilePath = configFilePath;
    }

    public void upgradeCluster() {
        System.out.println(ConsoleColors.BLUE_BOLD + "\n=== K3s version upgrade ===\n" + ConsoleColors.RESET);

        Util.checkKubectl();

        upgradeMaster();
        upgradeWorkers();

        System.out.println("Upgrade will now start. Run `watch kubectl get nodes` to see the nodes being upgraded. This should take a few minutes for a small cluster");
        System.out.println("The API server may be briefly unavailable during the upgrade of the controlplane");


        updateConfiguration();
    }

    private void upgradeWorkers() {
        NodePool[] workerNodePools = mainSettings.getWorkerNodePools();
        if (workerNodePools == null || workerNodePools.length == 0) {
            return;
        }

        long sum = Arrays.stream(workerNodePools).mapToLong(NodePool::getInstanceCount).sum();
        long concurrency = Math.max(sum / 4, 1);


        System.out.println("Creating workers upgrade plan.");
        Map<String, Object> worokerUpgradeData = new HashMap<>();
        worokerUpgradeData.put("new_k3s_version", newK3sVersion);
        worokerUpgradeData.put("worker_upgrade_concurrency", concurrency);
        String upgradeWorkerManifest = TemplateUtil.renderTemplate(TemplateUtil.UPGRADE_PLAN_MANIFEST_FOR_WORKERS, worokerUpgradeData);
        String command = "kubectl apply -f - <<EOF\n" + upgradeWorkerManifest + "\nEOF";

        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to create upgrade plan for workers");
            System.out.println(result.getOutput());
            System.out.println(result.getStatus());
            throw new RuntimeException("Failed to create upgrade plan for workers");
        }

        System.out.println("...workers upgrade plan created.");
    }


    private void upgradeMaster() {
        System.out.println("Creating master controlplane upgrade plan.");
        Map<String, Object> masterUpgradeManifestData = new HashMap<>();
        masterUpgradeManifestData.put("new_k3s_version", newK3sVersion);
        String upgradeMasterManifest = TemplateUtil.renderTemplate(TemplateUtil.UPGRADE_PLAN_MANIFEST_FOR_MASTERS, masterUpgradeManifestData);
        String command = "kubectl apply -f - <<EOF\n" + upgradeMasterManifest + "\nEOF";

        ShellUtil.ShellResult result = ShellUtil.run(command, mainSettings.getKubeconfigPath(), mainSettings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to create upgrade plan for master controlplane");
            System.out.println(result.getOutput());
            System.out.println(result.getStatus());
            throw new RuntimeException("Failed to create upgrade plan for master");
        }

        System.out.println("...master upgrade plan created.");
    }

    private void updateConfiguration() {
        String configurationFilePath = Util.replaceTildaWithFullHomePath(configFilePath);

        String currentConfiguration = null;
        try {
            Path path = Paths.get(configurationFilePath);
            currentConfiguration = new String(Files.readAllBytes(path));
            String newConfiguration = currentConfiguration.replaceAll("k3s_version: .*", "k3s_version: " + newK3sVersion);
            Files.write(path, newConfiguration.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
