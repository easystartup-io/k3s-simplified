package io.easystartup.utils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/*
 * @author indianBond
 */
public class TemplateUtil {
    public static final String CLOUD_INIT_YAML_PATH = "templates/cloud_init.yaml";

    public static String getTemplateFile(String file){
        InputStream resourceAsStream = TemplateUtil.class.getResourceAsStream(file);
        try {
            return IOUtils.toString(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
