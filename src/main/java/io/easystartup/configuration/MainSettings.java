package io.easystartup.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * @author indianBond
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MainSettings {
    public MainSettings() {
    }

    @JsonProperty("hetzner_token")
    private String hetznerToken;

    @JsonProperty("cluster_name")
    private String clusterName;

    @JsonProperty("kubeconfig_path")
    private String kubeconfigPath;

    @JsonProperty("k3s_version")
    private String k3SVersion;

    @JsonProperty("public_ssh_key_path")
    private String publicSSHKeyPath;

    @JsonProperty("private_ssh_key_path")
    private String privateSSHKeyPath;

    @JsonProperty("existing_network_name")
    private String existingNetworkName;

    @JsonProperty("use_ssh_agent")
    private boolean useSSHAgent;

    @JsonProperty("ssh_allowed_networks")
    private String[] sshAllowedNetworks;

    @JsonProperty("image")
    private String image ="ubuntu-22.04";

    @JsonProperty("autoscaling_image")
    private String autoScalingImage;

    @JsonProperty("additional_packages")
    private String[] additionalPackages;

    @JsonProperty("snapshot_od")
    private String snapshotOs ="default";

    @JsonProperty("api_allowed_networks")
    private String[] apiAllowedNetworks;

    @JsonProperty("private_network_subnet")
    private String privateNetworkSubnet = "10.0.0.0/16";

    @JsonProperty("private_api_load_balancer")
    private boolean privateApiLoadBalancer;

    @JsonProperty("cluster_cidr")
    private String clusterCIDR = "10.244.0.0/16";

    @JsonProperty("service_cidr")
    private String serviceCIDR = "10.43.0.0/16";

    @JsonProperty("cluster_dns")
    private String clusterDNS = "10.43.0.10";

    @JsonProperty("enable_public_net_ipv4")
    private boolean enablePublicNetIpv4 = true;

    @JsonProperty("enable_public_net_ipv6")
    private boolean enablePublicNetIpv6 = true;

    @JsonProperty("enable_encryption")
    private boolean enableEncryption;

    @JsonProperty("kube_api_server_args")
    private String[] kubeApiServerArgs;

    @JsonProperty("kube_scheduler_args")
    private String[] kubeSchedulerArgs;

    @JsonProperty("kube_controller_manager_args")
    private String[] kubeControllerManagerArgs;

    @JsonProperty("kube_cloud_controller_manager_args")
    private String[] kubeCloudControllerManagerArgs;

    @JsonProperty("kubelet_args")
    private String[] kubeletArgs;

    @JsonProperty("kube_proxy_args")
    private String[] kubeProxyArgs;

    @JsonProperty("api_server_hostname")
    private String apiServerHostname;

    @JsonProperty("disable_flannel")
    private boolean disableFlannel;

    @JsonProperty("schedule_workloads_on_masters")
    private boolean scheduleWorkloadsOnMasters;

    @JsonProperty("cloud_controller_manager_manifest_url")
    private String cloudControllerManagerManifestURL = "https://github.com/hetznercloud/hcloud-cloud-controller-manager/releases/download/v1.19.0/ccm-networks.yaml";

    @JsonProperty("csi_driver_manifest_url")
    private String csiDriverManifestURL = "https://raw.githubusercontent.com/hetznercloud/csi-driver/v2.6.0/deploy/kubernetes/hcloud-csi.yml";

    @JsonProperty("system_upgrade_controller_manifest_url")
    private String systemUpgradeControllerManifestURL = "https://raw.githubusercontent.com/rancher/system-upgrade-controller/master/manifests/system-upgrade-controller.yaml";

    @JsonProperty("masters_pool")
    private NodePool mastersPool;

    @JsonProperty("worker_node_pools")
    private NodePool[] nodePools;

    @JsonProperty("post_create_commands")
    private String[] postCreateCommands;

    @JsonProperty("ssh_port")
    private int sshPort = 22;

    @JsonProperty("ssh_port")
    private boolean debug;
    public String getHetznerToken() {
        return hetznerToken;
    }

    public void setHetznerToken(String value) {
        this.hetznerToken = value;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String value) {
        this.clusterName = value;
    }

    public String getKubeconfigPath() {
        return kubeconfigPath;
    }

    public void setKubeconfigPath(String value) {
        this.kubeconfigPath = value;
    }

    public String getK3SVersion() {
        return k3SVersion;
    }

    public void setK3SVersion(String value) {
        this.k3SVersion = value;
    }

    public String getPublicSSHKeyPath() {
        return publicSSHKeyPath;
    }

    public void setPublicSSHKeyPath(String value) {
        this.publicSSHKeyPath = value;
    }

    public String getPrivateSSHKeyPath() {
        return privateSSHKeyPath;
    }

    public void setPrivateSSHKeyPath(String value) {
        this.privateSSHKeyPath = value;
    }

    public boolean getUseSSHAgent() {
        return useSSHAgent;
    }

    public void setUseSSHAgent(boolean value) {
        this.useSSHAgent = value;
    }

    public String[] getSSHAllowedNetworks() {
        return sshAllowedNetworks;
    }

    public void setSSHAllowedNetworks(String[] value) {
        this.sshAllowedNetworks = value;
    }

    public String[] getAPIAllowedNetworks() {
        return apiAllowedNetworks;
    }

    public void setAPIAllowedNetworks(String[] value) {
        this.apiAllowedNetworks = value;
    }

    public String getPrivateNetworkSubnet() {
        return privateNetworkSubnet;
    }

    public void setPrivateNetworkSubnet(String value) {
        this.privateNetworkSubnet = value;
    }

    public boolean getEnablePublicNetIpv4() {
        return enablePublicNetIpv4;
    }

    public void setEnablePublicNetIpv4(boolean value) {
        this.enablePublicNetIpv4 = value;
    }

    public boolean getEnablePublicNetIpv6() {
        return enablePublicNetIpv6;
    }

    public void setEnablePublicNetIpv6(boolean value) {
        this.enablePublicNetIpv6 = value;
    }

    public String getAPIServerHostname() {
        return apiServerHostname;
    }

    public void setAPIServerHostname(String value) {
        this.apiServerHostname = value;
    }

    public boolean getDisableFlannel() {
        return disableFlannel;
    }

    public void setDisableFlannel(boolean value) {
        this.disableFlannel = value;
    }

    public boolean getScheduleWorkloadsOnMasters() {
        return scheduleWorkloadsOnMasters;
    }

    public void setScheduleWorkloadsOnMasters(boolean value) {
        this.scheduleWorkloadsOnMasters = value;
    }

    public String getCloudControllerManagerManifestURL() {
        return cloudControllerManagerManifestURL;
    }

    public void setCloudControllerManagerManifestURL(String value) {
        this.cloudControllerManagerManifestURL = value;
    }

    public String getCSIDriverManifestURL() {
        return csiDriverManifestURL;
    }

    public void setCSIDriverManifestURL(String value) {
        this.csiDriverManifestURL = value;
    }

    public String getSystemUpgradeControllerManifestURL() {
        return systemUpgradeControllerManifestURL;
    }

    public void setSystemUpgradeControllerManifestURL(String value) {
        this.systemUpgradeControllerManifestURL = value;
    }

    public NodePool getMastersPool() {
        return mastersPool;
    }

    public void setMastersPool(NodePool value) {
        this.mastersPool = value;
    }

    public NodePool[] getWorkerNodePools() {
        return nodePools;
    }

    public void setWorkerNodePools(NodePool[] value) {
        this.nodePools = value;
    }

    public String[] getPostCreateCommands() {
        return postCreateCommands;
    }

    public void setPostCreateCommands(String[] value) {
        this.postCreateCommands = value;
    }

    public boolean isUseSSHAgent() {
        return useSSHAgent;
    }

    public String[] getSshAllowedNetworks() {
        return sshAllowedNetworks;
    }

    public void setSshAllowedNetworks(String[] sshAllowedNetworks) {
        this.sshAllowedNetworks = sshAllowedNetworks;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getSnapshotOs() {
        return snapshotOs;
    }

    public void setSnapshotOs(String snapshotOs) {
        this.snapshotOs = snapshotOs;
    }

    public String[] getApiAllowedNetworks() {
        return apiAllowedNetworks;
    }

    public void setApiAllowedNetworks(String[] apiAllowedNetworks) {
        this.apiAllowedNetworks = apiAllowedNetworks;
    }

    public String getClusterCIDR() {
        return clusterCIDR;
    }

    public void setClusterCIDR(String clusterCIDR) {
        this.clusterCIDR = clusterCIDR;
    }

    public String getServiceCIDR() {
        return serviceCIDR;
    }

    public void setServiceCIDR(String serviceCIDR) {
        this.serviceCIDR = serviceCIDR;
    }

    public String getClusterDNS() {
        return clusterDNS;
    }

    public void setClusterDNS(String clusterDNS) {
        this.clusterDNS = clusterDNS;
    }

    public boolean isEnablePublicNetIpv4() {
        return enablePublicNetIpv4;
    }

    public boolean isEnablePublicNetIpv6() {
        return enablePublicNetIpv6;
    }

    public String getApiServerHostname() {
        return apiServerHostname;
    }

    public void setApiServerHostname(String apiServerHostname) {
        this.apiServerHostname = apiServerHostname;
    }

    public boolean isDisableFlannel() {
        return disableFlannel;
    }

    public boolean isScheduleWorkloadsOnMasters() {
        return scheduleWorkloadsOnMasters;
    }

    public String getCsiDriverManifestURL() {
        return csiDriverManifestURL;
    }

    public void setCsiDriverManifestURL(String csiDriverManifestURL) {
        this.csiDriverManifestURL = csiDriverManifestURL;
    }

    public NodePool[] getNodePools() {
        return nodePools;
    }

    public void setNodePools(NodePool[] nodePools) {
        this.nodePools = nodePools;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getExistingNetworkName() {
        return existingNetworkName;
    }

    public void setExistingNetworkName(String existingNetworkName) {
        this.existingNetworkName = existingNetworkName;
    }

    public String[] getAdditionalPackages() {
        return additionalPackages;
    }

    public void setAdditionalPackages(String[] additionalPackages) {
        this.additionalPackages = additionalPackages;
    }

    public boolean isPrivateApiLoadBalancer() {
        return privateApiLoadBalancer;
    }

    public void setPrivateApiLoadBalancer(boolean privateApiLoadBalancer) {
        this.privateApiLoadBalancer = privateApiLoadBalancer;
    }

    public boolean isEnableEncryption() {
        return enableEncryption;
    }

    public void setEnableEncryption(boolean enableEncryption) {
        this.enableEncryption = enableEncryption;
    }

    public String[] getKubeApiServerArgs() {
        return kubeApiServerArgs;
    }

    public void setKubeApiServerArgs(String[] kubeApiServerArgs) {
        this.kubeApiServerArgs = kubeApiServerArgs;
    }

    public String[] getKubeSchedulerArgs() {
        return kubeSchedulerArgs;
    }

    public void setKubeSchedulerArgs(String[] kubeSchedulerArgs) {
        this.kubeSchedulerArgs = kubeSchedulerArgs;
    }

    public String[] getKubeControllerManagerArgs() {
        return kubeControllerManagerArgs;
    }

    public void setKubeControllerManagerArgs(String[] kubeControllerManagerArgs) {
        this.kubeControllerManagerArgs = kubeControllerManagerArgs;
    }

    public String[] getKubeCloudControllerManagerArgs() {
        return kubeCloudControllerManagerArgs;
    }

    public void setKubeCloudControllerManagerArgs(String[] kubeCloudControllerManagerArgs) {
        this.kubeCloudControllerManagerArgs = kubeCloudControllerManagerArgs;
    }

    public String[] getKubeletArgs() {
        return kubeletArgs;
    }

    public void setKubeletArgs(String[] kubeletArgs) {
        this.kubeletArgs = kubeletArgs;
    }

    public String[] getKubeProxyArgs() {
        return kubeProxyArgs;
    }

    public void setKubeProxyArgs(String[] kubeProxyArgs) {
        this.kubeProxyArgs = kubeProxyArgs;
    }

    public String getAutoScalingImage() {
        return autoScalingImage;
    }

    public void setAutoScalingImage(String autoScalingImage) {
        this.autoScalingImage = autoScalingImage;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
