---
sidebar_position: 3
title: Basic Cluster Setup Guide
---
Created by [TitanFighter](https://github.com/TitanFighter)

## Step-by-Step Instructions

### Initial Project Setup with "hello-world"

We'll begin with a [hello-world](https://gist.githubusercontent.com/easystartup-io/448806b952b06f5053e3c1a731f470d5/raw/df9acc2ad49960afc4a396853fce1231f7ceed02/hello-world.yaml) application for initial testing.

1. Download `kubectl`: [Installation Guide](https://kubernetes.io/docs/tasks/tools/#kubectl).
2. Download `Helm`: [Installation Guide](https://helm.sh/docs/intro/install/).
3. Download `k3s-simplified`: [Getting Started Guide](https://k3s-simplified.easystartup.io/docs/getting-started/installation#installation-guide).
4. Create a `cluster_config.yaml` file using the configuration provided below. This configuration sets up a High Availability (HA) cluster with 3 master nodes and 3 worker nodes. You can opt for a smaller setup (1 master + 1 worker) for testing purposes:

```yaml
hetzner_token: nRTJFfhIGNT..........
cluster_name: hello-world  # k3s-simplified gives the next names to hosts: hello-world-cx21-master1 / hello-world-cpx21-pool-cpx31-worker1
kubeconfig_path: "./kubeconfig"  # or /cluster/kubeconfig if you are going to use Docker
k3s_version: v1.29.0+k3s1
public_ssh_key_path: "~/.ssh/id_rsa.pub"
private_ssh_key_path: "~/.ssh/id_rsa"
use_ssh_agent: true
ssh_allowed_networks:
  - 0.0.0.0/0
api_allowed_networks:
  - 0.0.0.0/0
schedule_workloads_on_masters: false
masters_pool:
  instance_type: cx21
  instance_count: 3
  location: nbg1
worker_node_pools:
- name: small
  instance_type: cpx21
  instance_count: 4
  location: hel1
- name: big
  instance_type: cpx31
  instance_count: 2
  location: fsn1
  autoscaling:
    enabled: true
    min_instances: 0
    max_instances: 3
```

5. Use `k3s-simplified` to create the cluster: `k3s-simplified create --config cluster_config.yaml`.
6. `k3s-simplified` will generate a `kubeconfig` file in the tool's run directory. To access your cluster with `kubectl` (installed in step 1), either move the `kubeconfig` file to `~/.kube/config` or set the environment variable `export KUBECONFIG=./kubeconfig`.

:::tip
Store all configurations in a specific folder to avoid repeatedly running `kubectl apply ...`. Use `kubectl apply -f /path/to/configs/ -R`.
:::

7. Create a new file: `touch ingress-nginx-annotations.yaml`.
8. Open and edit the new file: `nano ingress-nginx-annotations.yaml`.

```yaml
# INSTALLATION
# 1. Install Helm: https://helm.sh/docs/intro/install/
# 2. Add ingress-nginx help repo: helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
# 3. Update information of available charts locally from chart repositories: helm repo update
# 4. Install ingress-nginx:
# helm upgrade --install \
# ingress-nginx ingress-nginx/ingress-nginx \
# -f ./ingress-nginx-annotations.yaml \
# --namespace ingress-nginx \
# --create-namespace

# LIST of all ANNOTATIONS: https://github.com/hetznercloud/hcloud-cloud-controller-manager/blob/master/internal/annotation/load_balancer.go

controller:
  kind: DaemonSet
  service:
    annotations:
      # Germany:
      # - nbg1 (Nuremberg)
      # - fsn1 (Falkensteing)
      # Finland:
      # - hel1 (Helsinki)
      # USA:
      # - ash (Ashburn, Virginia)
      # Without this the load balancer won't be provisioned and will stay in "pending" state.
      # The state you can check via "kubectl get svc -n ingress-nginx"
      load-balancer.hetzner.cloud/location: nbg1

      # Name of load balancer. This name you will see in your Hetzner's cloud console (site) at the "Your project -> Load Balancers" page
      # NOTE: This is NOT the load balancer that the tool creates automatically for clusters with multiple masters (HA configuration). You need
      # to specify a different name here so it will create a separate load balancer for ingress Nginx.
      load-balancer.hetzner.cloud/name: WORKERS_LOAD_BALANCER_NAME

      # Ensures that the communication between the load balancer and the cluster nodes happens through the private network
      load-balancer.hetzner.cloud/use-private-ip: "true"

      # [ START: If you care about seeing the actual IP of the client then use these two annotations ]
      # - "uses-proxyprotocol" enables the proxy protocol on the load balancers so that ingress controller and
      # applications can "see" the real IP address of the client.
      # - "hostname" is needed just if you use cert-manager (LetsEncrypt SSL certificates). You need to use it in order
      # to fix fails http01 challenges of "cert-manager" (https://cert-manager.io/docs/).
      # Here (https://github.com/compumike/hairpin-proxy) you can find a description of this problem.
      # To be short: the easiest fix provided by some providers (including Hetzner) is to configure the load balancer so
      # that it uses a hostname instead of an IP.
      load-balancer.hetzner.cloud/uses-proxyprotocol: 'true'

      # 1. "yourDomain.com" must be configured in the DNS correctly to point to the Nginx load balancer,
      # otherwise the provision of certificates won't work;
      # 2. if you use a few domains, specify any one.
      load-balancer.hetzner.cloud/hostname: yourDomain.com
      # [ END: If you care about seeing the actual IP of the client then use these two annotations ]

      load-balancer.hetzner.cloud/http-redirect-https: 'false'
```

9. Add ingress-nginx repository: `helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx`.
10. Update local chart information: `helm repo update`.
11. Install ingress-nginx:

```bash
helm upgrade --install \
ingress-nginx ingress-nginx/ingress-nginx \
-f ~/.kube/ingress-nginx-annotations.yaml \
--namespace ingress-nginx \
--create-namespace
```

:::warning
To uninstall ingress-nginx and associated Hetzner load balancer: `helm uninstall ingress-nginx -n ingress-nginx`. This will delete current Hetzner's load balancer as a result when you install a new ingress controller, new Hetzner's load balancer will be created with a new public IP address.
:::

12. Verify that "EXTERNAL-IP" is assigned (not "pending"): `kubectl get svc -n ingress-nginx`.
13. In Hetzner's cloud console, locate the PUBLIC IP associated with `WORKERS_LOAD_BALANCER_NAME` from the `load-balancer.hetzner.cloud/name` annotation.
14. Download the hello-world application: `curl [hello-world URL] --output hello-world.yaml`.
15. Modify `hello-world.yaml` to include annotations and Hetzner's Load Balancer IP.

```yaml
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hello-world
  annotations:                             # <<<--- Add annotation
    kubernetes.io/ingress.class: nginx     # <<<--- Add annotation
spec:
  rules:
  - host: hello-world.IP_FROM_STEP_13.nip.io # <<<--- ADD IP FROM THE STEP 13.
  ....
```

16. Deploy the hello-world app: `kubectl apply -f hello-world.yaml`.
17. Test the application at `http://hello-world.IP_FROM_STEP_13.nip.io`. You should see the RANCHER Hello world! page.
"host.IP_FROM_STEP_13.nip.io" (the key part is ".nip.io") is just a quick way to test things without configuring DNS (a query to a hostname ending in nip.io simply returns the IP address it finds in the hostname itself).

18. For connecting a real domain, assign the IP from step 13 to your domain in the DNS registrar of your domain, and modify `- host: hello-world.IP_FROM_STEP_13.nip.io` in `hello-world.yaml` to use `- host: yourDomain.com`. Apply the changes `kubectl apply -f hello-world.yaml and wait 1-30mins for DNS update.

For LetsEncrypt SSL:

19. `load-balancer.hetzner.cloud/uses-proxyprotocol: "true"` annotation requires `use-proxy-protocol: "true"` for ingress-nginx, so let's create file: `touch ingress-nginx-configmap.yaml`
20. Add content to just created file: `nano ingress-nginx-configmap.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  # Do not change name - this is the name required by Nginx Ingress Controller
  name: ingress-nginx-controller
  namespace: ingress-nginx
data:
  use-proxy-protocol: "true"
```

21. Apply the config map: `kubectl apply -f ./ingress-nginx-configmap.yaml`.
22. Add the LetsEncrypt Helm repository: `helm repo add jetstack https://charts.jetstack.io`.
23. Update local chart information: `helm repo update`.
24. Install LetsEncrypt certificate issuer:

```bash
helm upgrade --install \
--namespace cert-manager \
--create-namespace \
--set installCRDs=true \
cert-manager jetstack/cert-manager
```

25. Create and apply the `lets-encrypt.yaml` file:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
  namespace: cert-manager
spec:
  acme:
    email: YOUR@EMAIL.com
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
    - http01:
        ingress:
          class: nginx
```

26. Apply file: `kubectl apply -f ./lets-encrypt.yaml`
27. Change `nano hello-world.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hello-world
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: "letsencrypt-prod"    # <<<--- Add annotation
    kubernetes.io/tls-acme: "true"                        # <<<--- Add annotation
spec:
  rules:
  - host: yourDomain.com  # <<<---- Your real domain
  tls: # <<<---- Add this block
  - hosts:
    - yourDomain.com
    secretName: yourDomain.com-tls # <<<--- Add reference to secret

  ....
```

27. Apply the updated hello-world application: `kubectl apply -f ./hello-world.yaml`.

The instructions are based on the README and [a specific GitHub discussion](https://github.com/vitobotta/hetzner-k3s/issues/13#issuecomment-901857297).

## FAQs

#### 1. What Load Balancers will be installed?
`k3s-simplified` installs/configures load balancer(s) for you via [Hetzner's cloud controller manager](https://github.com/hetznercloud/hcloud-cloud-controller-manager).
They cost money. The cheapest right now is 5 EUR/month.

If you are going to have a High Available (HA) cluster, you need to have 3/5/7/... (odd number) master nodes.
In this case you will get 2 Hetzner's load balancers:
- one for Kubernetes API (this one will be installed automatically by k3s-simplified);
- one for the ingress controller (for this one you need to add annotation "load-balancer.hetzner.cloud/location: XYZ" to ingress-nginx).

In case if you need to have just 1 master node, there will be just 1 Hetzner's load balancer - for the ingress controller (you need to add annotation "load-balancer.hetzner.cloud/location: XYZ" to ingress-nginx).

#### 2. Can we use "rules" block of ingress-nginx (the one which Kubernetes uses as a LoadBalancer) instead of creating our own "per-app" ingress as well as cert-manager like shown below

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
 name: ingress-nginx
 annotations:
   nginx.ingress.kubernetes.io/rewrite-target: /
   nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
   cert-manager.io/cluster-issuer: letsencrypt-prod
   kubernetes.io/ingress.class: nginx
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - yourDomain.com
    secretName: letsencrypt-certs
  rules:
  - host: yourDomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: myService
            port:
              number: 80
```

A separate ingresse resource for each app is recommended though. I also usually keep apps in separate namespaces, so in that case I need to have separate ingress resources.

#### 3. Is it possible to use for example MetalLB instead of Hetzner's LB?
There is a way to use MetalLB with floating IPs in Hetzner Cloud but I don't recommend it. The setup with standard load balancers is much simpler and load balancers are not that much more expensive than floating IPs so IMO there's no point using MetalLB.

#### 4. How to create and push docker images to a repository and how to allow kubernetes to work with this image (gitlab example)?
On a computer which creates an image:
- `docker login registry.gitlab.com`
- `docker build -t registry.gitlab.com/COMPANY_NAME/REPO_NAME:IMAGE_NAME -f /some/path/to/Dockerfile .`
- `docker push registry.gitlab.com/COMPANY_NAME/REPO_NAME:IMAGE_NAME`

On a computer which runs kubernetes:
- generate secret to access images: `kubectl create secret docker-registry gitlabcreds --docker-server=https://registry.gitlab.com --docker-username=MYUSER --docker-password=MYPWD --docker-email=MYEMAIL -n NAMESPACE_OF_YOUR_APP -o yaml > docker-secret.yaml`
- apply secret: `kubectl apply -f docker-secret.yaml -n NAMESPACE_OF_YOUR_APP`

#### 5. How to check how much resources nodes/pods use?
- Install metrics-server https://github.com/kubernetes-sigs/metrics-server
- Then use either `kubectl top nodes` or `kubectl top pods -A`

#### 6. What is Ingress?
There are 2 types of "ingress" -> `Ingress Controller` and `Ingress Resources`.
To simplify everything, in the case of Nginx...
- Ingress Controller is Nginx itself (this is `kind: Ingress`), Ingress Resources are services (ie. `kind: Service`).
- Ingress Controller has different annotations (rules). You can use them inside `kind: Ingress` as a result such rules become "global" and inside `kind: Service` as a result such rules become "local" (service-specific).
- Ingress Controller consists of a Pod and a Service. The Pod runs the Controller, which constantly polls the /ingresses endpoint on the API server of your cluster for updates to available Ingress Resources.

:::tip 
## Useful commands
```bash
kubectl get service [serviceName] -A or -n [nameSpace]
kubectl get ingress [ingressName] -A or -n [nameSpace]
kubectl get pod [podName] -A or -n [nameSpace]
kubectl get all -A
kubectl get events -A
helm ls -A
helm uninstall [name] -n [nameSpace]
kubectl -n ingress-nginx get svc
kubectl describe ingress -A
kubectl describe svc -n ingress-nginx
kubectl delete configmap nginx-config -n ingress-nginx
kubectl rollout restart deployment -n NAMESPACE_OF_YOUR_APP
kubectl get all -A` does not include "ingress", as a result you need to use `kubectl get ing -A
```
:::

:::tip 
## Useful links
1. [Kubernetes Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
2. [Troubleshooting Kubernetes Deployments](https://learnk8s.io/troubleshooting-deployments)
:::