package io.easystartup.cloud.hetzner.firewall;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.general.FirewallRule;
import me.tomsdevsn.hetznercloud.objects.request.CreateFirewallRequest;
import me.tomsdevsn.hetznercloud.objects.response.CreateFirewallResponse;
import me.tomsdevsn.hetznercloud.objects.response.FirewallsResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/*
 * @author indianBond
 */
public class Firewall {
    private final HetznerCloudAPI hetznerCloudAPI;

    public Firewall(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }

    public me.tomsdevsn.hetznercloud.objects.general.Firewall find(String name) {
        FirewallsResponse firewalls = hetznerCloudAPI.getFirewalls();
        Optional<me.tomsdevsn.hetznercloud.objects.general.Firewall> first = firewalls.getFirewalls().stream().filter(firewall -> firewall.getName().equals(name)).findFirst();
        return first.orElse(null);
    }

    public me.tomsdevsn.hetznercloud.objects.general.Firewall create(String firewallName, String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        CreateFirewallRequest.CreateFirewallRequestBuilder builder = CreateFirewallRequest.builder();
        builder.name(firewallName);

        builder.firewallRules(getFirewallRules(sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet));
        CreateFirewallResponse firewall = hetznerCloudAPI.createFirewall(builder.build());
        return firewall.getFirewall();
    }

    public me.tomsdevsn.hetznercloud.objects.general.Firewall createFirewallForNatGateway(String firewallName, String privateNetworkSubnet) {
        CreateFirewallRequest.CreateFirewallRequestBuilder builder = CreateFirewallRequest.builder();
        builder.name(firewallName);
        builder.firewallRules(getFirewallRulesForNatGateway(privateNetworkSubnet));
        CreateFirewallResponse firewall = hetznerCloudAPI.createFirewall(builder.build());
        return firewall.getFirewall();
    }

    private List<FirewallRule> getFirewallRulesForNatGateway(String privateNetworkSubnet) {
        List<FirewallRule> firewallRules = new ArrayList<>();
        firewallRules.add(allowICMPPing(privateNetworkSubnet));
        firewallRules.add(allowAllTrafficBetweenNodes(privateNetworkSubnet));
        firewallRules.add(allowUDPBetweenNodes(privateNetworkSubnet));
        return firewallRules;
    }

    public void update(Long firewallId, String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        List<FirewallRule> firewallRules = getFirewallRules(sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        hetznerCloudAPI.setFirewallRules(firewallId, firewallRules);
    }

    private FirewallRule allowKubernetesAPI(String[] apiAllowedNetworks) {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow port 6443 (Kubernetes API server)");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.tcp);
        builder.sourceIPs(List.of(apiAllowedNetworks));
        builder.destinationIPs(new ArrayList<>());
        builder.port("6443");
        return builder.build();
    }

    private FirewallRule allowUDPBetweenNodes(String privateNetworkSubnet) {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow all UDP traffic between nodes on the private network");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.udp);
        builder.sourceIPs(List.of(privateNetworkSubnet));
        builder.destinationIPs(new ArrayList<>());
        builder.port("any");
        return builder.build();
    }

    private FirewallRule allowAllTrafficBetweenNodes(String privateNetworkSubnet) {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow all TCP traffic between nodes on the private network");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.tcp);
        builder.sourceIPs(List.of(privateNetworkSubnet));
        builder.destinationIPs(new ArrayList<>());
        builder.port("any");
        return builder.build();
    }

    private FirewallRule allowICMPPing(String privateNetworkSubnet) {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow ICMP (ping)");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.icmp);
        List<String> sourceIPs = List.of("0.0.0.0/0", "::/0");
        if (StringUtils.isNotBlank(privateNetworkSubnet)){
            sourceIPs = List.of(privateNetworkSubnet);
        }
        builder.sourceIPs(sourceIPs);
        builder.destinationIPs(new ArrayList<>());
        return builder.build();
    }

    private FirewallRule allowSSHPort(String[] sshAllowedNetworks, int sshPort) {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow SSH port");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.tcp);
        builder.sourceIPs(Arrays.stream(sshAllowedNetworks).toList());
        builder.destinationIPs(new ArrayList<>());
        builder.port(String.valueOf(sshPort));
        return builder.build();
    }

    private List<FirewallRule> getFirewallRules(String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        List<FirewallRule> firewallRules = new ArrayList<>();

        firewallRules.add(allowSSHPort(sshAllowedNetworks, sshPort));
        firewallRules.add(allowICMPPing(null));
        firewallRules.add(allowAllTrafficBetweenNodes(privateNetworkSubnet));
        firewallRules.add(allowUDPBetweenNodes(privateNetworkSubnet));
        if (highAvailability) {
            firewallRules.add(allowKubernetesAPI(apiAllowedNetworks));
        }
        return firewallRules;
    }

    public void delete(Long id) {
        hetznerCloudAPI.deleteFirewall(id);
    }
}
