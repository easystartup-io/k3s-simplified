---
sidebar_position: 1
---

# Proxy Protocol

The Proxy Protocol in the context of an NGINX Ingress controller is used to preserve the original client IP address for requests that pass through a load balancer. 

When you use a load balancer that terminates TCP connections, the Proxy Protocol helps in ensuring that the backend service can see the real source IP of the incoming requests.

To configure an NGINX Ingress controller to use the Proxy Protocol, you can make the following adjustments to your configuration:

1. **In the Helm `values.yaml` file** of the nginx-ingress controller: Set `use-proxy-protocol` to `"true"` in the `config` section. 
2. Additionally, add the annotation `load-balancer.hetzner.cloud/uses-proxyprotocol: 'true'`

```yaml
  controller:
  service:
    annotations:
      load-balancer.hetzner.cloud/location: ash
      load-balancer.hetzner.cloud/name: ingress-nginx
      load-balancer.hetzner.cloud/type: lb11
      load-balancer.hetzner.cloud/hostname: easystartup.example.com
      load-balancer.hetzner.cloud/http-redirect-https: 'false'
      # highlight-next-line 
      load-balancer.hetzner.cloud/uses-proxyprotocol: 'true'
      load-balancer.hetzner.cloud/use-private-ip: 'true'
  replicaCount: 2
  config:
    use-proxy-protocol: "true"
```

This setup helps in maintaining the client's real IP address, which is crucial for security, logging, and compliance purposes. It's especially useful when your ingress traffic is routed through a load balancer that otherwise masks the source IP. 

For more detailed configuration options and steps, you can refer to the NGINX Ingress controller documentation and guides available online.

### Links

1. [Supported hetzner load balancer controller annotations](https://pkg.go.dev/github.com/hetznercloud/hcloud-cloud-controller-manager/internal/annotation#Name)