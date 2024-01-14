package io.easystartup.cloud.hetzner.location;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.response.LocationsResponse;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * @author indianBond
 */
public class Location {

    private final HetznerCloudAPI hetznerCloudAPI;

    public Location(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }

    public Set<String> getLocations() {
        LocationsResponse locations = hetznerCloudAPI.getLocations();
        return locations.getLocations().stream().map(me.tomsdevsn.hetznercloud.objects.general.Location::getName).collect(Collectors.toSet());
    }

    public me.tomsdevsn.hetznercloud.objects.general.Location getLocation(String location) {
        LocationsResponse locationByName = hetznerCloudAPI.getLocationByName(location);
        return locationByName.getLocations().stream().findFirst().orElse(null);
    }
}
