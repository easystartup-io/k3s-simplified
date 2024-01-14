---
sidebar_position: 1
---

# Introduction

Securing your cluster from external threats such as excessive pings or hackers is crucial. Our primary objective is to isolate the cluster from the public network as much as possible, enhancing its security.

## Prerequisites for Isolation
To achieve this, we need to set up:

1. **Private API Load Balancer:** Manages internal traffic.
2. **NAT Gateway with a Public IP:** Provides internet access and enables downloading of k3s for worker and master nodes.
3. **Access/Jump Box Server:** Sits within the private network for executing commands.
4. **Disable Public Networking on Node IPs:** Ensures node accessibility only through the access box.
5. **Post-Install Routing Command:** Routes outbound access of worker and master nodes through the NAT gateway.

### Setting up the Private API Load Balancer
Enable a private API load balancer by editing `cluster_config.yaml`:

```yaml
private_api_load_balancer: true # Set to true for private load balancing. Ensure it's accessible from your location.
```

### Disabling Public Networking on Node IPs
To disable public networking, modify `cluster_config.yaml`:

```yaml
enable_public_net_ipv4: false # Default is true. Set to false to disable IPv4 public networking.
enable_public_net_ipv6: false # Default is true. Set to false to disable IPv6 public networking.
```

### Configuring a NAT Gateway
A detailed guide for setting up a NAT Gateway can be found here:
[Configure NAT Gateway](https://community.hetzner.com/tutorials/how-to-set-up-nat-for-cloud-networks)

### Post-Installation Node Configuration
For ARM-based images, add the following commands to `cluster_config.yaml`:

```yaml
post_create_commands:
  - >
    printf "network## {config## disabled}" |
    sed 's/##/:/g' > /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg
  - >
    printf "network##\n  version## 2\n  renderer## networkd\n  ethernets##\n    enp7s0##\n      dhcp4## true\n      nameservers##\n        addresses## [185.12.64.1, 185.12.64.2]\n      routes##\n        - to## default\n          via## 10.0.0.1" |
    sed 's/##/:/g' > /etc/netplan/50-cloud-init.yaml
  - netplan generate
  - netplan apply
  - apt update
  - apt upgrade -y
  - apt install nfs-common -y
  - apt autoremove -y

```

For other image types, apply these commands to the specific node pool configuration.

```yaml
- name: mongo-operator
  instance_type: cpx31
  instance_count: 1
  location: hel1
  labels:
    - key: purpose
      value: mongo-operator
  post_create_commands:
    - >
      printf "network## {config## disabled}" |
      sed 's/##/:/g' > /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg
    - >
      printf "network##\n  version## 2\n  renderer## networkd\n  ethernets##\n    enp7s0##\n      dhcp4## true\n      nameservers##\n        addresses## [185.12.64.1, 185.12.64.2]\n      routes##\n        - to## default\n          via## 10.0.0.1" |
      sed 's/##/:/g' > /etc/netplan/50-cloud-init.yaml
    - netplan generate
    - netplan apply
    - apt update
    - apt upgrade -y
    - apt install nfs-common -y
    - apt autoremove -y
```

:::note
The network interface name (like `enp7s0` or `ens10`) varies based on the instance type. Refer to [Hetzner's documentation](https://docs.hetzner.com/cloud/networks/server-configuration/#debian--ubuntu) for guidance. Use `ip a` command on your master/worker node to identify all attached interfaces.
:::

Reference to issue on hetzner-k3s
https://github.com/vitobotta/hetzner-k3s/discussions/252