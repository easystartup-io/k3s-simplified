---
title: Upgrading k3s-simplified binary
sidebar_position: 3
---

To upgrade the k3s-simplified binary to the latest version, you simply need to execute the installation commands once more. This process will overwrite the current binary with the newest version available. I ensure backward compatibility with each release, allowing you to seamlessly update to the most recent version without any hassle.

```bash
wget https://github.com/easystartup-io/k3s-simplified/releases/latest/download/k3s-simplified.tar.gz -O k3s-simplified.tar.gz --backups=0
tar -xf k3s-simplified.tar.gz
cd k3s-simplified
chmod +x install.sh
sudo ./install.sh
cd ..
```

By following these commands, you will update your k3s-simplified binary to the latest version, ensuring you have access to all the latest features and improvements.