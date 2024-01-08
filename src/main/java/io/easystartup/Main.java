package io.easystartup;


import io.easystartup.configuration.ConfigurationLoader;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

/*
 * @author indianBond
 */
public class Main {

    public static void main(String[] args) {
        int execute = new CommandLine(new K3sSimplifier()).execute(args);
        System.exit(execute);
    }

    @Command(name = "k3s-simplifier",
            mixinStandardHelpOptions = true,
            version = "k3s-simplifier 1.0",
            description = "A tool to create k3s clusters on any Cloud"
    )
    public static class K3sSimplifier implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            return null;
        }

        @Command(name = "create", description = "# Create a cluster")
        public void createCluster() {
        }

        @Command(name = "delete", description = "# Delete a cluster")
        public void deleteCluster() {
            System.out.println("Not yet implemented");
        }

        @Command(name = "releases", description = "# List the available k3s releases")
        public void listReleases() {
            System.out.println("Not yet implemented");
        }

        @Command(name = "upgrade", description = "# Upgrade a cluster to a new version of k3s")
        public void upgradeCluster() {
            System.out.println("Not yet implemented");
        }
    }

    @Command(name = "crete", description = "# Create a cluster")
    public static class CreateCluster implements Runnable {
        @CommandLine.Option(names = {"-c", "--config"}, required = true, description = "# The path of the YAML configuration file")
        private String config;

        @Override
        public void run() {
            ConfigurationLoader configurationLoader = new ConfigurationLoader(config);
            configurationLoader.validate();
            for (String error : configurationLoader.getErrors()) {
                System.out.println(error);
            }
            new CreateNewCluster(configurationLoader.getSettings()).initializeCluster();
        }
    }

}