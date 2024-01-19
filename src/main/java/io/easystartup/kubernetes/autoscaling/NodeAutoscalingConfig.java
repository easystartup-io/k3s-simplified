package io.easystartup.kubernetes.autoscaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeAutoscalingConfig {
    private String cloudInit;
    private Map<String, String> labels;
    private List<Taint> taints;

    public NodeAutoscalingConfig() {
    }

    public String getCloudInit() {
        return cloudInit;
    }

    public void setCloudInit(String cloudInit) {
        this.cloudInit = cloudInit;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public List<Taint> getTaints() {
        return taints;
    }

    public void setTaints(List<Taint> taints) {
        this.taints = taints;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Taint {
        private String key;
        private String value;
        private String effect;

        public Taint() {
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }
    }
}
