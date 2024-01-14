package io.easystartup.cloud.hetzner.placementgroup;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.enums.PlacementGroupType;
import me.tomsdevsn.hetznercloud.objects.request.CreatePlacementGroupRequest;
import me.tomsdevsn.hetznercloud.objects.response.PlacementGroupResponse;
import me.tomsdevsn.hetznercloud.objects.response.PlacementGroupsResponse;

import java.util.Optional;

/*
 * @author indianBond
 */
public class PlacementGroup {
    private final HetznerCloudAPI hetznerCloudAPI;

    public PlacementGroup(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }
    public me.tomsdevsn.hetznercloud.objects.general.PlacementGroup create(String name) {
        CreatePlacementGroupRequest.CreatePlacementGroupRequestBuilder builder = CreatePlacementGroupRequest.builder();
        builder.name(name);
        builder.type(PlacementGroupType.spread);
        PlacementGroupResponse placementGroup = hetznerCloudAPI.createPlacementGroup(builder.build());
        return placementGroup.getPlacementGroup();
    }

    public me.tomsdevsn.hetznercloud.objects.general.PlacementGroup find(String name) {
        PlacementGroupsResponse placementGroups = hetznerCloudAPI.getPlacementGroup(name);
        return placementGroups.getPlacementGroups().stream().findFirst().orElse(null);
    }

    public void delete(Long id) {
        hetznerCloudAPI.deletePlacementGroup(id);
    }
}
