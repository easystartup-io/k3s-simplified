package io.easystartup.cloud.hetzner;

import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.general.Location;
import me.tomsdevsn.hetznercloud.objects.general.Network;
import me.tomsdevsn.hetznercloud.objects.general.ServerType;
import me.tomsdevsn.hetznercloud.objects.response.LocationResponse;
import me.tomsdevsn.hetznercloud.objects.response.LocationsResponse;
import me.tomsdevsn.hetznercloud.objects.response.NetworksResponse;
import me.tomsdevsn.hetznercloud.objects.response.ServerTypesResponse;

import java.util.Set;
import java.util.stream.Collectors;

/*
 * @author indianBond
 */
public class HetznerClient {

    private String token;
    private HetznerCloudAPI hetznerCloudAPI;

    public HetznerClient(String token) {
        this.token = token;
        this.hetznerCloudAPI = new HetznerCloudAPI(token);
    }

    public Set<String> getNetworks() {
        NetworksResponse networks = hetznerCloudAPI.getNetworks();
        return networks.getNetworks().stream().map(Network::getName).collect(Collectors.toSet());
    }

    public Set<String> getServerTypes() {
        ServerTypesResponse serverTypes = hetznerCloudAPI.getServerTypes();
        return serverTypes.getServerTypes().stream().map(ServerType::getName).collect(Collectors.toSet());
    }

    public Set<String> getLocations() {
        LocationsResponse locations = hetznerCloudAPI.getLocations();
        return locations.getLocations().stream().map(Location::getName).collect(Collectors.toSet());
    }
}
