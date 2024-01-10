package io.easystartup.utils;

import io.easystartup.configuration.NodePool;
import me.tomsdevsn.hetznercloud.objects.general.Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/*
 * @author indianBond
 */
public class SSHUtil {
    public static void ssh(Server server,int port, )
    public static ShellResult sshinternal(String command, String kubeconfigPath, String hetznerToken) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("sh", "-c", command);
            builder.directory(new File(System.getProperty("user.home")));
            Map<String, String> env = builder.environment();
            env.put("KUBECONFIG", kubeconfigPath);
            env.put("HETZNER_TOKEN", hetznerToken);

            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            return new ShellResult(exitCode == 0, output.toString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return new ShellResult(false, e.getMessage());
        }
    }

    public static class ShellResult {
        private boolean success;
        private String output;

        public ShellResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getOutput() {
            return output;
        }
    }
}
