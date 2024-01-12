# k3s-simplified: Hetzner Kubernetes Cluster Made Easy

---

![GitHub Latest Release](https://img.shields.io/github/v/release/easyStartup-pulse/k3s-simplified)
![GitHub Release Date](https://img.shields.io/github/release-date/easyStartup-pulse/k3s-simplified)
![GitHub Last Commit](https://img.shields.io/github/last-commit/easyStartup-pulse/k3s-simplified)
![GitHub Issues](https://img.shields.io/github/issues-raw/easyStartup-pulse/k3s-simplified)
![GitHub Pull Requests](https://img.shields.io/github/issues-pr-raw/easyStartup-pulse/k3s-simplified)
![GitHub License](https://img.shields.io/github/license/easyStartup-pulse/k3s-simplified)
![GitHub Discussions](https://img.shields.io/github/discussions/easyStartup-pulse/k3s-simplified)
![GitHub Top Language](https://img.shields.io/github/languages/top/easyStartup-pulse/k3s-simplified)
![GitHub Forks](https://img.shields.io/github/forks/easyStartup-pulse/k3s-simplified?style=social)
![GitHub Repo Stars](https://img.shields.io/github/stars/easyStartup-pulse/k3s-simplified?style=social)

---

## Introduction: Effortless Kubernetes Clusters on Hetzner Cloud

**k3s-simplified** streamlines Kubernetes deployment in Hetzner Cloud, offering a Java-based open-source alternative to hetzner-k3s. It runs as a standalone binary without dependencies, ensuring ease of maintenance and support. Aimed at production environments, it emphasizes privacy and security, keeping vital components like the API load balancer private.

### Origin & Development

- Originated from [hetzner-k3s](https://github.com/vitobotta/hetzner-k3s).
- Java port for cross-platform compatibility.
- Active development with a focus on private, isolated clusters for production use.
- Offers full support and paid consultancy services.

### Tool Overview

k3s-simplified is a command-line tool for rapid Kubernetes cluster creation and management in Hetzner Cloud. It leverages k3s, a lightweight Kubernetes distribution by Rancher, known for its minimal resource consumption and quick deployment.

- **Rapid Deployment**: Set up a fully functional HA k3s cluster with master and worker nodes in 2-3 minutes.
- **Comprehensive Setup**: Includes infrastructure creation (servers, networks, firewalls), k3s deployment, and essential Kubernetes components like Hetzner Cloud Controller Manager, Hetzner CSI Driver, Rancher System Upgrade Controller, and Cluster Autoscaler.
- **Detailed Tutorial**: Check the [wiki page](https://github.com/easyStartup-pulse/k3s-simplified/blob/main/wiki/Setting%20up%20a%20cluster.md) for step-by-step setup instructions.

---

## Getting Started with k3s-simplified

### Prerequisites

- Hetzner Cloud account.
- API token with read/write permissions.
- kubectl installation.

### Installation Guide



#### Installation Steps

1. **Binary Installation for macOS and Linux** (Apple Silicon/MacOS/LINUX M1/amd64/aarch64):
    ```bash
    wget https://github.com/easyStartup-pulse/k3s-simplified/releases/latest/download/k3s-simplifier.tar.gz
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
# [Configuration details such as Hetzner token, cluster name, SSH keys, network settings...]
# [Masters and worker node pools configurations]
# [Additional settings for cloud controller manager, CSI driver, system upgrade controller, and more]
```

For a detailed breakdown of each setting, please refer to the official repository documentation.

### Creating the Cluster

Execute the following command with your configuration file:

```bash
k3s-simplified create --config cluster_config.yaml
```

### Additional Cluster Operations

- **Adding Nodes**: Modify the instance count in the config file and rerun the create command.
- **Scaling Down**: Decrease the instance count and delete the nodes from both Kubernetes and Hetzner Cloud.
- **Upgrading k3s Version**: Use the `k3s-simplified upgrade` command with the new version number.
- **Deleting a Cluster**: Run `k3s-simplified delete --config cluster_config.yaml`.

---

## Troubleshooting and Support

Encounter issues? Check the SSH key setup or use the SSH agent. For more complex problems, the GitHub discussions and issues sections are great resources.

---

## Acknowledgments and Licensing

- Inspired by [hetzner-k3s](https://github.com/vitobotta/hetzner-k3s).
- Utilizes [hetznercloud-java](https://github.com/tomsiewert/hetznercloud-java) for cloud APIs.
- Licensed under the [MIT License](https://github.com/easyStartup-pulse/k3s-simplified/blob/main/LICENSE.txt).

---

## Stargazers and Community

Join our growing community and track our progress!

[![Stargazers over time](https://starchart.cc/easyStartup-pulse/k3s-simplified.svg)](https://starchart.cc/easyStartup-pulse/k3s-simplified)

---

Explore k3s-simplified for an efficient, secure, and manageable Kubernetes deployment in Hetzner Cloud! 🚀