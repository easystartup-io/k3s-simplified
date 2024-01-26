---
sidebar_position: 1
title: Installation  
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
    wget https://github.com/easystartup-io/k3s-simplified/releases/latest/download/k3s-simplified.tar.gz -O k3s-simplified.tar.gz --backups=0
    tar -xf k3s-simplified.tar.gz
    cd k3s-simplified
    chmod +x install.sh
    sudo ./install.sh
    cd ..
    ```

2. **Windows Users**: Utilize the Linux binary under [WSL (Windows Subsystem for Linux)](https://learn.microsoft.com/en-us/windows/wsl/install).