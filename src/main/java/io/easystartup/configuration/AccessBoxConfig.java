package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessBoxConfig {

    private NodePool node;

    public AccessBoxConfig() {
    }

    public NodePool getNode() {
        return node;
    }

    public void setNode(NodePool node) {
        this.node = node;
    }
}
