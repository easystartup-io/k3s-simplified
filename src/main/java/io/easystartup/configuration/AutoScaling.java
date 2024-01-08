package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoScaling {

    private boolean enabled;
    @JsonProperty(value = "min_instances")
    private Integer minInstances;
    @JsonProperty(value = "max_instances")
    private Integer maxInstances;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMinInstances() {
        return minInstances;
    }

    public void setMinInstances(Integer minInstances) {
        this.minInstances = minInstances;
    }

    public Integer getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(Integer maxInstances) {
        this.maxInstances = maxInstances;
    }
}
