---
title: Upgrading Your k3s Cluster
sidebar_position: 1
---

To upgrade your cluster to a newer k3s version for the first time, execute the command below:

```bash
hetzner-k3s upgrade --config cluster_config.yaml --new-k3s-version v1.29.2+k3s1
```

This command requires you to add the target k3s version as an extra argument. The upgrade process will automatically update your configuration file to reflect the new version. To view a list of available k3s versions, use `hetzner-k3s releases`.

:::warning
Please note that for single master clusters, there will be a temporary downtime of the API server as the control plane is upgraded.
:::

To monitor the upgrade's progress, execute `watch kubectl get nodes -owide`. This command allows you to observe the sequential upgrade of master nodes, followed by the worker nodes.

Important: If you aren't seeing the latest release, you might need to delete the `releases list yaml file` file in your temp directory to update the available k3s version list. Just run 
```bash
rm -f /tmp/k3s-simplified-releases*
```

### Troubleshooting a Problematic Upgrade

Should the upgrade encounter issues or fail to upgrade all nodes:

1. Remove all existing upgrade plans and jobs, then restart the upgrade controller with the following commands:

```bash
kubectl -n system-upgrade delete job --all
kubectl -n system-upgrade delete plan --all

kubectl label node --all plan.upgrade.cattle.io/k3s-server- plan.upgrade.cattle.io/k3s-agent-

kubectl -n system-upgrade rollout restart deployment system-upgrade-controller
kubectl -n system-upgrade rollout status deployment system-upgrade-controller
```

To view the system upgrade controller's pod logs, use:

```bash
kubectl -n system-upgrade \
  logs -f $(kubectl -n system-upgrade get pod -l pod-template-hash -o jsonpath="{.items[0].metadata.name}")
```

If the upgrade halts after the master nodes but before the worker nodes, clearing resources as outlined might not suffice. In such instances, execute the following to signal that the masters have been upgraded, allowing the worker nodes' upgrade to proceed:

```bash
kubectl label node <master1> <master2> <master3> plan.upgrade.cattle.io/k3s-server=upgraded
```