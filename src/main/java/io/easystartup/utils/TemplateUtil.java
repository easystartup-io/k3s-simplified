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
    public static final String CLOUD_INIT_YAML_PATH = "templates/cloud_init.yaml";
    public static final String MASTER_INSTALL_SCRIPT = "templates/master_install_script.sh";
    public static final String WORKER_INSTALL_SCRIPT = "templates/worker_install_script.sh";
    public static final String HETZNER_CLOUD_SECRET_MANIFEST = "templates/hetzner_cloud_secret_manifest.yaml";
    public static final String CLUSTER_AUTOSCALER_MANIFEST = "templates/cluster_autoscaler.yaml";

    public static String getTemplateFile(String file){
        InputStream resourceAsStream = TemplateUtil.class.getResourceAsStream(file);
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
