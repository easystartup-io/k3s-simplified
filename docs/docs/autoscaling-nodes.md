---
sidebar_position: 70
---

# Autoscaling Nodes

### What is Cluster Autoscaler 

Cluster Autoscaler is a tool that automatically adjusts the size of the Kubernetes cluster when one of the following conditions is true:

* There are pods that failed to run in the cluster due to insufficient resources.
* There are nodes in the cluster that have been underutilized for an extended period of time and their pods can be placed on other existing nodes.


:::note
This document is only pertaining to auto scaling nodes, and not related to autoscaling pods based on utilization metrics. For pod stats based autoscaling configure your pod metrics and use kubernetes pod based horizontal or pod based vertical autoscaler.
:::

:::success INFO
For autoscaling nodes I have used the official kubernetes [Cluster Autoscaler for Hetzner](https://github.com/kubernetes/autoscaler/blob/master/cluster-autoscaler/cloudprovider/hetzner/README.md) .
:::

### To add autoscaling to nodes, just add autoscaling params

1. **Enable autoscaling for the node pool**

    Enable autoscaling by setting `enabled` to true

    ```yaml
    worker_node_pools:
      - name: big-autoscaled
        instance_type: cpx31
        instance_count: 2
        location: fsn1
        # highlight-start
        autoscaling:
          enabled: true
        # highlight-end
          min_instances: 0
          max_instances: 3
    ```

2. **Set the required instance counts**

    Set your `min_instances` and `max_instances` according to your use case

    ```yaml
    worker_node_pools:
      - name: big-autoscaled
        instance_type: cpx31
        instance_count: 2
        location: fsn1
        autoscaling:
          enabled: true
        # highlight-start
          min_instances: 0
          max_instances: 3
        # highlight-end
    ```

3. **Set the right images**

    Set the images to be used for autoscaling in cluster_config.yaml file

    ```yaml
    autoscaling_image_x64: 67794396 #Image to be used for x64 instance autoscaling, 
    autoscaling_image_arm64: 103908130 #Image to be used for arm instance autoscaling
    ```

    :::warning
    If you dont specify the above images, the default fallback order is `autoscaling_image` -> `image` -> `ubuntu-22.04`
    :::

    There is a limitation in the official hetzner kuberentes controller where you cannot set custom images for each autoscaled node group. Currently you can only only set a default image for all arm instances and all x86 instances. In the future when support will be added, this tool will also use the image present in the node pool.

4. **Remaining node pool config gets used in the autoscaled node group also**

    Your labels, taints, post_create_commands configs etc will be applied to the autoscaled group also.

:::danger VERIFY BEFORE ENABLING AUTOSCALING
Make sure that you have enough hetzner server limit quota to be able to scale up nodes. Else those new nodes might not come up if you exceed your max quota of server limit.
:::

### Links for further understanding

1. [Hetzner cluster autoscaler](https://github.com/kubernetes/autoscaler/blob/master/cluster-autoscaler/cloudprovider/hetzner/README.md)
2. [Kubernets cluster autoscaler FAQ](https://github.com/kubernetes/autoscaler/blob/master/cluster-autoscaler/FAQ.md#frequently-asked-questions)
3. [Medium article on how kubernetes autoscaling works](https://medium.com/kubecost/understanding-kubernetes-cluster-autoscaling-675099a1db92)