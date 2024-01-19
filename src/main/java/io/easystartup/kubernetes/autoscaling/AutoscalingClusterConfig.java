package io.easystartup.kubernetes.autoscaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoscalingClusterConfig {
    private AutoScalingImagesForArch autoScalingImagesForArch;
    private Map<String, NodeAutoscalingConfig> nodeConfigs;

    public AutoscalingClusterConfig() {
    }

    public AutoScalingImagesForArch getImagesForArch() {
        return autoScalingImagesForArch;
    }

    public void setImagesForArch(AutoScalingImagesForArch autoScalingImagesForArch) {
        this.autoScalingImagesForArch = autoScalingImagesForArch;
    }

    public Map<String, NodeAutoscalingConfig> getNodeConfigs() {
        return nodeConfigs;
    }

    public void setNodeConfigs(Map<String, NodeAutoscalingConfig> nodeConfigs) {
        this.nodeConfigs = nodeConfigs;
    }
}
