package io.easystartup.installer;

import io.easystartup.CreateNewCluster;
import io.easystartup.cloud.hetzner.HetznerClient;
import io.easystartup.configuration.MainSettings;
import me.tomsdevsn.hetznercloud.objects.general.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.easystartup.utils.Util.sleep;

/*
 * @author indianBond
 */
public class KubernetesInstaller {
    private final MainSettings mainSettings;
    private final HetznerClient hetznerClient;
    private final LoadBalancer loadBalancer;
    private final Map<CreateNewCluster.ServerType, List<Server>> servers;

    private final Network network;
    private final Firewall firewall;
    private final SSHKey sshKey;

    public KubernetesInstaller(MainSettings mainSettings, HetznerClient hetznerClient, LoadBalancer loadBalancer, Map<CreateNewCluster.ServerType, List<Server>> servers, Network network, Firewall firewall, SSHKey sshKey) {
        this.mainSettings = mainSettings;
        this.hetznerClient = hetznerClient;
        this.loadBalancer = loadBalancer;
        this.servers = servers;
        this.network = network;
        this.firewall = firewall;
        this.sshKey = sshKey;
    }


    public void startInstallation() {
        System.out.println("\n=== Setting up Kubernetes ===\n");

        List<Server> serverList = servers.get(CreateNewCluster.ServerType.MASTER);
        setUpFirstMaster(serverList.get(0));
        setUpOtherMasters(serverList);
        setUpWorkers(servers.get(CreateNewCluster.ServerType.WORKER));

        System.out.println("\n=== Deploying Hetzner drivers ===\n");

        Util.checkKubectl();

        addLabelsAndTaintsToMasters();
        addLabelsAndTaintsToWorkers();

        createHetznerCloudSecret();
        deployCloudControllerManager();
        deployCsiDriver();
        deploySystemUpgradeController();
        deployClusterAutoscaler();
    }

    private void setUpFirstMaster(Server firstMaster) {
        System.out.println("Deploying k3s to first master " + firstMaster.getName() + "...");

        String output = ssh.run(firstMaster, settings.getSshPort(), masterInstallScript(firstMaster), settings.isUseSshAgent());

        System.out.println("Waiting for the control plane to be ready...");

        sleep(10000); // Sleep for 10 seconds

        saveKubeconfig();

        System.out.println("...k3s has been deployed to first master " + firstMaster.getName() + " and the control plane is up.");
    }

    private void setUpOtherMasters(List<Server> masters) {
        if (masters.size() < 2) {
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(masters.size() - 1);
        List<Future<?>> futures = new ArrayList<>();

        for (Server master : masters.subList(1, masters.size())) {
            futures.add(executor.submit(() -> deployK3sToMaster(master)));
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

    private void setUpWorkers(List<Server> workers) {
        if (CollectionUtils.isEmpty(workers)){
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());
        List<Future<?>> futures = new ArrayList<>();

        for (Server worker : workers) {
            futures.add(executor.submit(() -> deployK3sToWorker(worker)));
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

    private void deployK3sToMaster(Server master) {
        System.out.println("Deploying k3s to master " + master.getName() + "...");
        ssh.run(master, mainSettings.getSshPort(), masterInstallScript(master), mainSettings.isUseSSHAgent());
        System.out.println("...k3s has been deployed to master " + master.getName() + ".");
    }

    private void deployK3sToWorker(Server worker) {
        System.out.println("Deploying k3s to worker " + worker.getName() + "...");
        ssh.run(worker, mainSettings.getSshPort(), workerInstallScript(), mainSettings.isUseSSHAgent());
        System.out.println("...k3s has been deployed to worker " + worker.getName() + ".");
    }

    private void createHetznerCloudSecret() {
        System.out.println("\nCreating secret for Hetzner Cloud token...");

        String secretManifest = renderTemplate(HETZNER_CLOUD_SECRET_MANIFEST, settings.getHetznerToken());
        String command = buildKubectlApplyCommand(secretManifest);

        ShellResult result = Util.shellRun(command, configuration.getKubeconfigPath(), settings.getHetznerToken());

        if (!result.isSuccess()) {
            System.out.println("Failed to create Hetzner Cloud secret:");
            System.out.println(result);
            throw new RuntimeException("Failed to create Hetzner Cloud secret");
        }

        System.out.println("...secret created.");
    }
}
