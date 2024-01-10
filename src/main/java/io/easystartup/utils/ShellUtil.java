package io.easystartup.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/*
 * @author indianBond
 */
public class ShellUtil {

    public static ShellResult run(String command, String kubeconfigPath, String hetznerToken)  {
        String cmdFilePath = "/tmp/cli.cmd";
        try {
            writeFile(cmdFilePath, "#!/bin/bash\nset -euo pipefail\n" + command, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        new File(cmdFilePath).setExecutable(true);

        ProcessBuilder builder = new ProcessBuilder("bash", "-c", cmdFilePath);
        Map<String, String> env = builder.environment();
        env.put("KUBECONFIG", kubeconfigPath);
        env.put("HCLOUD_TOKEN", hetznerToken);

        Process process = null;
        try {
            process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());
            int status = process.waitFor();
            return new ShellResult(status == 0 ? output : error, status);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeFile(String path, String content, boolean append) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, append))) {
            writer.write(content);
        }
    }

    public static class ShellResult {
        private final String output;
        private final int status;

        public ShellResult(String output, int status) {
            this.output = output;
            this.status = status;
        }

        public String getOutput() {
            return output;
        }

        public int getStatus() {
            return status;
        }

        public boolean isSuccess() {
            return status == 0;
        }
    }
}
