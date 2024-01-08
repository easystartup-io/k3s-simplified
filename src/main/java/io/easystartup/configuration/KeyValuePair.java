package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeyValuePair {
    private String key;
    private String value;

    public KeyValuePair(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
