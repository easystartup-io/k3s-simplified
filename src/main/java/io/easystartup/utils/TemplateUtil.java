package io.easystartup.utils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/*
 * @author indianBond
 */
public class TemplateUtil {

    private String getTemplateFile(String file){
        InputStream resourceAsStream = this.getClass().getResourceAsStream(file);
        try {
            return IOUtils.toString(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
