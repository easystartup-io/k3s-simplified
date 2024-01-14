package io.easystartup.cloud.hetzner.network;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.enums.SubnetType;
import me.tomsdevsn.hetznercloud.objects.general.Subnet;
import me.tomsdevsn.hetznercloud.objects.request.CreateNetworkRequest;
import me.tomsdevsn.hetznercloud.objects.response.NetworkResponse;
import me.tomsdevsn.hetznercloud.objects.response.NetworksResponse;

import java.util.List;

/*
 * @author indianBond
 */
public class Network {
    private final HetznerCloudAPI hetznerCloudAPI;

    public Network(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }

    public me.tomsdevsn.hetznercloud.objects.general.Network find(String existingNetworkName) {
        NetworksResponse networksByName = hetznerCloudAPI.getNetworksByName(existingNetworkName);
        return networksByName.getNetworks().stream().findFirst().orElse(null);
    }

    public void delete(long id) {
        hetznerCloudAPI.deleteNetwork(id);
    }

    public me.tomsdevsn.hetznercloud.objects.general.Network create(String name, String privateNetworkSubnet, String networkZone) {
        CreateNetworkRequest.CreateNetworkRequestBuilder builder = CreateNetworkRequest.builder();
        builder.name(name);
        builder.ipRange(privateNetworkSubnet);
        Subnet subnet = new Subnet();
        subnet.setType(SubnetType.cloud);
        subnet.setNetworkZone(networkZone);
        subnet.setIpRange(privateNetworkSubnet);
        builder.subnets(List.of(subnet));
        NetworkResponse network = hetznerCloudAPI.createNetwork(builder.build());
        return network.getNetwork();
    }


}
