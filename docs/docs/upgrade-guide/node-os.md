---
title: Upgrading Node Operating Systems
sidebar_position: 2
---

- Consider adding an extra node temporarily if your cluster lacks spare capacity.
- Sequentially drain, update, and reboot each node, then return it to service.
- To streamline this process, install the [Kubernetes Reboot Daemon (Kured)](https://kured.dev/). Ensure the nodes' OS supports unattended upgrades, especially for security patches. For Ubuntu nodes, include the following in your configuration file before creation:

```yaml
additional_packages:
- unattended-upgrades
- update-notifier-common
post_create_commands:
- sudo systemctl enable unattended-upgrades
- sudo systemctl start unattended-upgrades
```

Review the Kured documentation for details on setting maintenance windows and other configurations.