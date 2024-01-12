package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodePool {

    public NodePool() {
    }

    @JsonProperty("name")
    private String name;

    @JsonProperty("instance_type")
    private String instanceType;

    @JsonProperty("instance_count")
    private long instanceCount = 1;

    @JsonProperty("location")
    private String location;

    @JsonProperty("labels")
    private KeyValuePair[] labels;
    @JsonProperty("taints")
    private KeyValuePair[] taints;

    @JsonProperty("image")
    private String image;


    @JsonProperty("post_create_commands")
    private String[] postCreateCommands;

    @JsonProperty("additional_packages")
    private String[] additionalPackages;

    private AutoScaling autoScaling;

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String value) {
        this.instanceType = value;
    }

    public long getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(long value) {
        this.instanceCount = value;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String value) {
        this.location = value;
    }

    public KeyValuePair[] getLabels() {
        return labels;
    }

    public void setLabels(KeyValuePair[] value) {
        this.labels = value;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String value) {
        this.image = value;
    }

    public KeyValuePair[] getTaints() {
        return taints;
    }

    public void setTaints(KeyValuePair[] value) {
        this.taints = value;
    }

    public String[] getPostCreateCommands() {
        return postCreateCommands;
    }

    public void setPostCreateCommands(String[] value) {
        this.postCreateCommands = value;
    }

    public String[] getAdditionalPackages() {
        return additionalPackages;
    }

    public void setAdditionalPackages(String[] additionalPackages) {
        this.additionalPackages = additionalPackages;
    }

    public AutoScaling getAutoScaling() {
        return autoScaling;
    }

    public void setAutoScaling(AutoScaling autoScaling) {
        this.autoScaling = autoScaling;
    }
}
