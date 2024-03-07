---
sidebar_position: 70
title: Config Reference Guide
---

## Mandatary Options for cluster

| Field | Default | Description|
|-------|---------|------------|
|hetzner_token||Hetzner token for your project|
|cluster_name||Name of your k3s kubernetes cluster|
|kubeconfig_path|./kubeconfig|After cluster creation, the kubeconfig for your cluster to be able to connect to your kubernetes cluster|
|k3s_version|v1.29.2+k3s1|K3s version you want to use|
|public_ssh_key_path||you need to generate ssh key pair and point to the public ssh path|
|private_ssh_key_path||you need to generate ssh key pair and point to the private ssh path|
| ssh_allowed_networks          | ["0.0.0.0/0"]        | Networks allowed for SSH access.                                                                                                                                        |
| api_allowed_networks          | ["0.0.0.0/0"]        | Networks allowed for API access.                                                                                                                                        |
| masters_pool                  | | Configuration for the master nodes pool, including instance type, count, and location.                                                                                  |

## Config Options for cluster

| Field | Default | Description|
|-------|---------|------------|
| schedule_workloads_on_masters | false               | Determines whether workloads can be scheduled on master nodes.                                                                                                          |
| worker_node_pools             |  | Configuration for worker node pools, including names, instance types, counts, locations, and autoscaling configurations for the "big" pool. |