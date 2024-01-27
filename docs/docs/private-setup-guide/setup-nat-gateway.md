---
sidebar_position: 3
---

# Setup Nat gateway

:::info
Skip this step if you have already setup nat gateway. And add it to your private network.
:::

## What is a Nat Gateway?

Using nat gateway you can connect to public internet

### 1. Setup using k3s-simplified

In your cluster_config.yaml ensure that you fill in all the other details as required in the final cluster and add the natGatewayConfig field also.

:::warning
Ensure that the node instance type and location you select is in the same region as your other nodes. Because of this [hetzner limitation](https://docs.hetzner.com/cloud/general/locations/#are-there-any-restrictions).
:::

```yaml
hetzner_token: <token>
cluster_name: test-k3s
kubeconfig_path: "./kubeconfig"
k3s_version: v1.29.0+k3s1
public_ssh_key_path: "~/.ssh/hetzner_rsa.pub"
private_ssh_key_path: "~/.ssh/hetzner_rsa"
# ... other config items
// highlight-start
natGatewayConfig:
  node:
    instance_type: cax21
    location: nbg1
    image: 103908130
// highlight-end
```
:::info
Currently only ubuntu 22.04 based nat-gateway can be created, because the installation scripts are only supported for it. For other env please create your own nat gateway or please help in contributing.
:::

### 2. Run the command to create the access box

```bash
k3s-simplified create-nat-gateway --config cluster_config.yaml
```

It will give you the output of the ip to your nat gateway. And then you can add this to your post install script of worker and master nodes

### Conclusion

Setting up a nat gateway is a critical step in ensuring that your private-network only client servers (master and worker k8s nodes) are able to access the public internal urls. This is required to be able to download and upgrade k3s, download images from public registry as well as for any kubernetes applications which need to access outside urls.

### Helpful links
1. [Hetzner NAT Guide](https://community.hetzner.com/tutorials/how-to-set-up-nat-for-cloud-networks)
2. [Hetzner Network interface Guide](https://docs.hetzner.com/cloud/networks/server-configuration/#debian--ubuntu)