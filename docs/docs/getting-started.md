---
sidebar_position: 2
title: Getting started
---

# Getting Started with k3s-simplified

### Prerequisites

- Hetzner Cloud account.
- Hetzner Cloud Token with read/write permissions (You need to create a project from the cloud console, and then an API token with * both read and write permissions* (sidebar > Security > API Tokens); you will see the token only once, so be sure to take note of it somewhere safe).
- kubectl installation.

### Installation Guide



#### Installation Steps

1. **Binary Installation for macOS and Linux** (Apple Silicon/MacOS/LINUX M1/amd64/aarch64):
    ```bash
    wget https://github.com/easystartup-io/k3s-simplified/releases/latest/download/k3s-simplifier.tar.gz
    tar -xf k3s-simplifier.tar.gz
    cd k3s-simplifier
    chmod +x install.sh
    sudo ./install.sh
    ```

2. **Windows Users**: Utilize the Linux binary under [WSL (Windows Subsystem for Linux)](https://learn.microsoft.com/en-us/windows/wsl/install).

---

## Creating Your Cluster

k3s-simplified requires a YAML configuration file for cluster operations. Here's an example template (with optional settings commented):

```yaml
---
hetzner_token: <your token>
cluster_name: test
kubeconfig_path: "./kubeconfig"
k3s_version: v1.29.0+k3s1
public_ssh_key_path: "~/.ssh/id_rsa.pub"
private_ssh_key_path: "~/.ssh/id_rsa"
use_ssh_agent: false # set to true if your key has a passphrase or if SSH connections don't work or seem to hang without agent. See https://github.com/easystartup-io/k3s-simplified#limitations
# ssh_port: 22
ssh_allowed_networks:
  - 0.0.0.0/0 # ensure your current IP is included in the range
api_allowed_networks:
  - 0.0.0.0/0 # ensure your current IP is included in the range
private_network_subnet: 10.0.0.0/16 # ensure this doesn't overlap with other networks in the same project
disable_flannel: false # set to true if you want to install a different CNI
schedule_workloads_on_masters: false
# debug: true # To see in realtime what scripts are being executed, beware it will print the tokens so please dont run when in hostile env
# cluster_cidr: 10.244.0.0/16 # optional: a custom IPv4/IPv6 network CIDR to use for pod IPs
# service_cidr: 10.43.0.0/16 # optional: a custom IPv4/IPv6 network CIDR to use for service IPs
# cluster_dns: 10.43.0.10 # optional: IPv4 Cluster IP for coredns service. Needs to be an address from the service_cidr range
# enable_public_net_ipv4: false # default is true
# enable_public_net_ipv6: false # default is true
# private_api_load_balancer: true # default is false, if you want to use a private api load balancer, ensure that its accessible from where you are running executing this config 
# api_server_hostname: testcluster.example.com # DNS for the k8s API LoadBalancer. Only enable after you have run the create command at least once and done the dns mapping to the private ip or public ip of the load balancer . Else the kubectl command will dns timeout
# image: rocky-9 # optional: default is ubuntu-22.04
# autoscaling_image: 103908130 # optional, defaults to the `image` setting
# snapshot_os: microos # otional: specified the os type when using a custom snapshot
cloud_controller_manager_manifest_url: "https://github.com/hetznercloud/hcloud-cloud-controller-manager/releases/download/v1.19.0/ccm-networks.yaml"
csi_driver_manifest_url: "https://raw.githubusercontent.com/hetznercloud/csi-driver/v2.6.0/deploy/kubernetes/hcloud-csi.yml"
system_upgrade_controller_manifest_url: "https://raw.githubusercontent.com/rancher/system-upgrade-controller/master/manifests/system-upgrade-controller.yaml"
masters_pool:
  instance_type: cpx21
  instance_count: 3
  location: nbg1
worker_node_pools:
  - name: small-static
    instance_type: cpx21
    instance_count: 4
    location: hel1
    # image: debian-11
    # labels:
    #   - key: purpose
    #     value: blah
    # taints:
    #   - key: something
    #     value: value1:NoSchedule
  - name: big-autoscaled
    instance_type: cpx31
    instance_count: 2
    location: fsn1
    autoscaling:
      enabled: true
      min_instances: 0
      max_instances: 3
# additional_packages:
# - somepackage
# post_create_commands:
# - apt update
# - apt upgrade -y
# - apt autoremove -y
# enable_encryption: true
# existing_network: <specify if you want to use an existing network, otherwise one will be created for this cluster>
# kube_api_server_args:
# - arg1
# - ...
# kube_scheduler_args:
# - arg1
# - ...
# kube_controller_manager_args:
# - arg1
# - ...
# kube_cloud_controller_manager_args:
# - arg1
# - ...
# kubelet_args:
# - arg1
# - ...
# kube_proxy_args:
# - arg1
# - ...
```

For a detailed breakdown of each setting, please refer to the official repository documentation.

### Creating the Cluster

Execute the following command with your configuration file:

```bash
k3s-simplified create --config cluster_config.yaml
```

:::info Tip: Idempotent
This create command can be run any number of times with the same configuration without causing any issue, since this is idempotent. 
This means that if for some reason the create process gets stuck or throws errors (for example if the Hetzner API is unavailable or there are timeouts etc), you can just stop the current command, and re-run it with the same configuration to continue from where it left.
:::


### Additional Cluster Operations

- **Adding Nodes**: Modify the instance count in the config file and rerun the create command.
- **Scaling Down**: Decrease the instance count and delete the nodes from both Kubernetes and Hetzner Cloud.
- **Upgrading k3s Version**: Use the `k3s-simplified upgrade` command with the new version number.
- **Deleting a Cluster**: Run `k3s-simplified delete --config cluster_config.yaml`.

---

## Troubleshooting and Support

Encounter issues? Check the SSH key setup or use the SSH agent. For more complex problems, the GitHub discussions and issues sections are great resources.

Explore k3s-simplified for an efficient, secure, and manageable Kubernetes deployment in Hetzner Cloud! 🚀