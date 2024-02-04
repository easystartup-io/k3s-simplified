package io.easystartup.utils;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/*
 * @author indianBond
 */
public class TemplateUtil {

    // Access Box

    public static final String ACCESS_BOX_COPY_SSH_KEYS = "templates/accessBox/copy_ssh_keys.sh";
    public static final String ACCESS_BOX_COPY_CLUSTER_CONFIG = "templates/accessBox/copy_cloud_config.sh";
    public static final String ACCESS_BOX_INSTALL_K3S_SIMPLIFIED = "templates/accessBox/install_k3s_simplified.sh";
    public static final String ACCESS_BOX_INSTALL_KUBECTL = "templates/accessBox/install_kubectl.sh";
    // -----

    // Nat Gateway
    public static final String NAT_GATEWAY_SETUP = "templates/natGateway/setup.sh";
    // -----
    public static final String CLOUD_INIT_YAML_PATH = "templates/cloud_init.yaml";
    public static final String MASTER_INSTALL_SCRIPT = "templates/master_install_script.sh";
    public static final String WORKER_INSTALL_SCRIPT = "templates/worker_install_script.sh";
    public static final String HETZNER_CLOUD_SECRET_MANIFEST = "templates/hetzner_cloud_secret_manifest.yaml";
    public static final String CLUSTER_AUTOSCALER_MANIFEST = "templates/cluster_autoscaler.yaml";
    public static final String CLUSTER_AUTOSCALER_MANIFEST_V2 = "templates/cluster_autoscaler_v2.yaml";

    // Upgrade k3s cluster
    public static final String UPGRADE_PLAN_MANIFEST_FOR_MASTERS = "templates/upgrade_plan_for_masters.yaml";
    public static final String UPGRADE_PLAN_MANIFEST_FOR_WORKERS = "templates/upgrade_plan_for_workers.yaml";

    public static String getTemplateFile(String file){
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
        try {
            return IOUtils.toString(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String renderTemplate(String templateName, Map<String, Object> dataModel) {
        Jinjava jinjava = new Jinjava();
        String template = getTemplateFile(templateName);
        return jinjava.render(template, dataModel);
    }
}
