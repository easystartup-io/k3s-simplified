package io.easystartup.utils;

import java.io.File;
import java.nio.file.Paths;

/*
 * @author indianBond
 */
public class Util {

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
//                e.printStackTrace();
        }
    }

    public static boolean isExecutableAvailable(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        String[] paths = System.getenv("PATH").split(File.pathSeparator);
        for (String path : paths) {
            File cmdFile = Paths.get(path, command).toFile();
            if (cmdFile.isFile() && cmdFile.canExecute()) {
                return true;
            }
            if (isWindows) {
                File cmdFileExe = Paths.get(path, command + ".exe").toFile();
                if (cmdFileExe.isFile() && cmdFileExe.canExecute()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void checkKubectl() {
        if (!isExecutableAvailable("kubectl")) {
            System.out.println("Please ensure kubectl is installed and in your PATH.");
            System.exit(1);
        }
    }

    public static String replaceTildaWithFullHomePath(String path) {
        return path.startsWith("~") ? path.replaceFirst("~", System.getProperty("user.home")) : path;
    }

    public static String replaceFullHomePathWithTilda(String path) {
        String home = System.getProperty("user.home");
        return path.startsWith(home) ? path.replaceFirst(home, "~") : path;
    }

}
