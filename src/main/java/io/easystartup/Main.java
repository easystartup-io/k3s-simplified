package io.easystartup;


import io.easystartup.configuration.ConfigurationLoader;
import io.easystartup.utils.Releases;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.simple.SimpleLogger;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
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

    @Command(name = "k3s-simplified",
            mixinStandardHelpOptions = true,
            versionProvider = K3sSimplifier.PropertiesVersionProvider.class,
            synopsisHeading = "Usage:%n   ",
            description = "A tool to create k3s clusters on any Cloud",
            synopsisSubcommandLabel = "[Subcommand]",
            commandListHeading = "%nSubcommands:%n",
            subcommands = {
                    CreateCluster.class,
                    DeleteCluster.class
            }
    )
    public static class K3sSimplifier implements Callable<Integer> {
        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            throw new CommandLine.ParameterException(spec.commandLine(), "Please enter a command like \"create\" or \"delete\" etc...\n");
        }

        @Command(name = "releases", description = "# List the available k3s releases")
        public void listReleases() {
            List<String> releaseName = new Releases().availableReleases();
            for (String release : releaseName) {
                System.out.println(release);
            }
        }

        @Command(name = "upgrade", description = "# Upgrade a cluster to a new version of k3s")
        public void upgradeCluster() {
            System.out.println("Not yet implemented");
        }

        static class PropertiesVersionProvider implements CommandLine.IVersionProvider {
            public String[] getVersion() throws Exception {
                ClassLoader loader = getClass().getClassLoader();
                Properties properties = new Properties();
                try (InputStream resourceStream = loader.getResourceAsStream("version.properties")) {
                    properties.load(resourceStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new String[]{"k3s-simplified " + properties.getProperty("version")};
            }
        }
    }

    @Command(name = "create", description = "# Create a cluster")
    public static class CreateCluster implements Runnable {
        @CommandLine.Option(names = {"-c", "--config"}, required = true, description = "# The path of the YAML configuration file")
        private String config;

        @Override
        public void run() {
            try {
                ConfigurationLoader configurationLoader = new ConfigurationLoader(config);
                configurationLoader.validate();
                for (String error : configurationLoader.getErrors()) {
                    System.out.println(error);
                }
                if (CollectionUtils.isNotEmpty(configurationLoader.getErrors())) {
                    return;
                }
                new io.easystartup.cluster.CreateCluster(configurationLoader.getSettings()).initializeCluster();
            } catch (Throwable throwable) {
                System.out.println(throwable.getMessage());
            }
        }
    }

    @Command(name = "delete", description = "# Delete a cluster")
    public static class DeleteCluster implements Runnable {
        @CommandLine.Option(names = {"-c", "--config"}, required = true, description = "# The path of the YAML configuration file")
        private String config;

        @Override
        public void run() {
            try {
                ConfigurationLoader configurationLoader = new ConfigurationLoader(config);
                configurationLoader.validate();
                for (String error : configurationLoader.getErrors()) {
                    System.out.println(error);
                }
                if (CollectionUtils.isNotEmpty(configurationLoader.getErrors())) {
                    return;
                }
                new io.easystartup.cluster.DeleteCluster(configurationLoader.getSettings()).deleteCluster();
            } catch (Throwable throwable) {
                System.out.println(throwable.getMessage());
            }
        }
    }

    private static void setLoggerProperties() {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");
        System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
    }

}