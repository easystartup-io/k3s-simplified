package io.easystartup.cloud.hetzner.loadbalancer;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.enums.TargetType;
import me.tomsdevsn.hetznercloud.objects.general.LBService;
import me.tomsdevsn.hetznercloud.objects.general.LBTarget;
import me.tomsdevsn.hetznercloud.objects.general.LBTargetLabelSelector;
import me.tomsdevsn.hetznercloud.objects.general.LoadBalancer;
import me.tomsdevsn.hetznercloud.objects.request.CreateLoadBalancerRequest;
import me.tomsdevsn.hetznercloud.objects.request.CreateLoadBalancerRequestAlgorithmType;
import me.tomsdevsn.hetznercloud.objects.response.LoadBalancerResponse;
import me.tomsdevsn.hetznercloud.objects.response.LoadBalancersResponse;

import java.util.List;
import java.util.Optional;

/*
 * @author indianBond
 */
public class Loadbalancer {

    private final HetznerCloudAPI hetznerCloudAPI;

    public Loadbalancer(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }

    public LoadBalancer find(String loadBalancerName) {
        LoadBalancersResponse loadBalancers = hetznerCloudAPI.getLoadBalancerByName(loadBalancerName);
        Optional<LoadBalancer> first = loadBalancers.getLoadBalancers().stream().findFirst();
        return first.orElse(null);
    }

    public LoadBalancer createK8sAPILoadBalancer(String clusterName, Long networkId, boolean privateApiLoadBalancer, String location) {
        CreateLoadBalancerRequest.CreateLoadBalancerRequestBuilder builder = CreateLoadBalancerRequest.builder();
        builder.loadBalancerType("lb11");
        builder.location(location);
        builder.name(clusterName + "-api");
        builder.network(networkId);
        builder.publicInterface(!privateApiLoadBalancer);

        LBService lbService = new LBService();
        lbService.setDestinationPort(6443L);
        lbService.setListenPort(6443L);
        lbService.setProtocol("tcp");
        lbService.setProxyprotocol(false);
        builder.services(List.of(lbService));

        LBTarget lbTarget = new LBTarget();
        lbTarget.setType(TargetType.label_selector);
        lbTarget.setLabelSelector(new LBTargetLabelSelector("cluster=" + clusterName + ",role=master"));
        lbTarget.setUsePrivateIp(true);
        builder.targets(List.of(lbTarget));

        builder.algorithm(new CreateLoadBalancerRequestAlgorithmType("round_robin"));
        LoadBalancerResponse loadBalancer = hetznerCloudAPI.createLoadBalancer(builder.build());
        return loadBalancer.getLoadBalancer();
    }

    public void delete(Long id) {
        hetznerCloudAPI.deleteLoadBalancer(id);
    }
}
