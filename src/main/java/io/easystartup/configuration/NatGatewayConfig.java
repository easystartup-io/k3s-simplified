package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NatGatewayConfig {

    private NodePool node;

    public NatGatewayConfig() {
    }

    public NodePool getNode() {
        return node;
    }

    public void setNode(NodePool node) {
        this.node = node;
    }
}
