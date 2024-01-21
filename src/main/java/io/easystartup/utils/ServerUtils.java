package io.easystartup.utils;

import io.easystartup.configuration.MainSettings;
import me.tomsdevsn.hetznercloud.objects.general.Server;

import java.util.concurrent.TimeUnit;

/*
 * @author indianBond
 */
public class ServerUtils {

    public static void waitForServerToComeUp(Server server, SSH ssh, MainSettings mainSettings) {
        System.out.println("Waiting for successful SSH connectivity with server " + server.getName() + "...");

        long tic = System.currentTimeMillis();
        while (true) {
            try {
                TimeUnit.SECONDS.sleep(1); // Waiting 1 second before retry

                // Implement a timeout mechanism here if necessary
                String result = ssh.ssh(server, mainSettings.getSshPort(), "echo ready", mainSettings.isUseSSHAgent());

                if ("ready".equals(result.trim())) {
                    break; // Break the loop if the expected result is achieved
                }
            } catch (Throwable throwable) {
                long tac = System.currentTimeMillis();
                if ((tac - tic) > TimeUnit.MINUTES.toMillis(4)) {
                    System.out.println("facing issue while connecting to " + server.getName());
                    throwable.printStackTrace();
                    throw new RuntimeException(throwable);
                }
            }
        }

        System.out.println("...server " + server.getName() + " is now up.");
    }
}
