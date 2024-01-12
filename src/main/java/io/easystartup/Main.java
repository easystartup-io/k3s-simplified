package io.easystartup;


import io.easystartup.configuration.ConfigurationLoader;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.simple.SimpleLogger;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

/*
 * @author indianBond
 */
public class Main {

    public static void main(String[] args) {
        setLoggerProperties();
        int execute = new CommandLine(new K3sSimplifier()).execute(args);
        System.exit(execute);
    }

    @Command(name = "k3s-simplifier",
            mixinStandardHelpOptions = true,
            version = "k3s-simplifier 1.0",
            synopsisHeading = "Usage:%n   ",
            description = "A tool to create k3s clusters on any Cloud",
            synopsisSubcommandLabel = "[Subcommand]",
            commandListHeading = "%nSubcommands:%n",
            subcommands = {
                    CreateCluster.class
            }
    )
    public static class K3sSimplifier implements Callable<Integer> {
        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            throw new CommandLine.ParameterException(spec.commandLine(), "Please enter a command like \"create\" or \"delete\" etc...\n");
//            return null;
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

    @Command(name = "create", description = "# Create a cluster")
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
            if (CollectionUtils.isNotEmpty(configurationLoader.getErrors())) {
                return;
            }
            new CreateNewCluster(configurationLoader.getSettings()).initializeCluster();
        }
    }

    private static void setLoggerProperties() {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");
        System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
    }

}