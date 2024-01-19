package io.easystartup.kubernetes.autoscaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoScalingImagesForArch {

    private String arm64;
    private String amd64;

    public AutoScalingImagesForArch() {
    }

    public String getArm64() {
        return arm64;
    }

    public void setArm64(String arm64) {
        this.arm64 = arm64;
    }

    public String getAmd64() {
        return amd64;
    }

    public void setAmd64(String amd64) {
        this.amd64 = amd64;
    }
}
