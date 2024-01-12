package io.easystartup.cloud.hetzner;

import io.easystartup.utils.TemplateUtil;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.enums.PlacementGroupType;
import me.tomsdevsn.hetznercloud.objects.enums.SubnetType;
import me.tomsdevsn.hetznercloud.objects.enums.TargetType;
import me.tomsdevsn.hetznercloud.objects.general.*;
import me.tomsdevsn.hetznercloud.objects.request.*;
import me.tomsdevsn.hetznercloud.objects.response.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.easystartup.utils.Util.sleep;

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

    public Location getLocation(String location) {
        LocationsResponse locations = hetznerCloudAPI.getLocations();
        Optional<Location> first = locations.getLocations().stream().filter(val -> val.getName().equals(location)).findFirst();
        return first.orElse(null);
    }

    public Network findNetwork(String existingNetworkName) {
        NetworksResponse networks = hetznerCloudAPI.getNetworks();
        Optional<Network> first = networks.getNetworks().stream().filter(val -> val.getName().equals(existingNetworkName)).findFirst();
        return first.orElse(null);
    }

    public Network createNetwork(String name, String privateNetworkSubnet, String networkZone) {
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

    public Firewall findFireWall(String name) {
        FirewallsResponse firewalls = hetznerCloudAPI.getFirewalls();
        Optional<Firewall> first = firewalls.getFirewalls().stream().filter(firewall -> firewall.getName().equals(name)).findFirst();
        return first.orElse(null);
    }

    public Firewall createFirewall(String firewallName, String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        CreateFirewallRequest.CreateFirewallRequestBuilder builder = CreateFirewallRequest.builder();
        builder.name(firewallName);

        builder.firewallRules(getFirewallRules(sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet));
        CreateFirewallResponse firewall = hetznerCloudAPI.createFirewall(builder.build());
        return firewall.getFirewall();
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

    private FirewallRule allowICMPPing() {
        FirewallRule.FirewallRuleBuilder builder = FirewallRule.builder();
        builder.description("Allow ICMP (ping)");
        builder.direction(FirewallRule.Direction.in);
        builder.protocol(FirewallRule.Protocol.icmp);
        builder.sourceIPs(List.of("0.0.0.0/0", "::/0"));
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

    public void updateFirewall(Long firewallId, String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        List<FirewallRule> firewallRules = getFirewallRules(sshAllowedNetworks, apiAllowedNetworks, highAvailability, sshPort, privateNetworkSubnet);
        hetznerCloudAPI.setFirewallRules(firewallId, firewallRules);
    }

    private List<FirewallRule> getFirewallRules(String[] sshAllowedNetworks, String[] apiAllowedNetworks, boolean highAvailability, int sshPort, String privateNetworkSubnet) {
        List<FirewallRule> firewallRules = new ArrayList<>();

        firewallRules.add(allowSSHPort(sshAllowedNetworks, sshPort));
        firewallRules.add(allowICMPPing());
        firewallRules.add(allowAllTrafficBetweenNodes(privateNetworkSubnet));
        firewallRules.add(allowUDPBetweenNodes(privateNetworkSubnet));
        if (highAvailability) {
            firewallRules.add(allowKubernetesAPI(apiAllowedNetworks));
        }
        return firewallRules;
    }

    public SSHKey getSSHKey(String publicSSHKeyPath) {
        SSHKeysResponse sshKey = hetznerCloudAPI.getSSHKeys();
        String fingerPrint = null;
        try {
            fingerPrint = calculateFingerprint(publicSSHKeyPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String finalFingerPrint = fingerPrint;
        Optional<SSHKey> first = sshKey.getSshKeys().stream().filter(sshKey1 -> sshKey1.getFingerprint().equals(finalFingerPrint)).findFirst();
        return first.orElse(null);
    }

    private static String calculateFingerprint(String publicSSHKeyPath) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(publicSSHKeyPath));
        String keyContent = new String(keyBytes);
        String privateKey = keyContent.split("\\s+")[1];

        byte[] decodedKey = Base64.getDecoder().decode(privateKey);
        byte[] digest = MessageDigest.getInstance("MD5").digest(decodedKey);

        // Convert the byte array to a hex string with colons
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            String hex = Integer.toHexString(0xff & digest[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
            if (i < digest.length - 1) {
                hexString.append(":");
            }
        }

        return hexString.toString();
    }

    public SSHKey createSSHKey(String clusterName, String publicSSHKeyPath) {
        try {
            String publicKey = new String(Files.readAllBytes(Paths.get(publicSSHKeyPath))).trim();
            CreateSSHKeyRequest.CreateSSHKeyRequestBuilder builder = CreateSSHKeyRequest.builder();
            builder.name(clusterName);
            builder.publicKey(publicKey);
            return hetznerCloudAPI.createSSHKey(builder.build()).getSshKey();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PlacementGroup createPlacementGroup(String name) {
        CreatePlacementGroupRequest.CreatePlacementGroupRequestBuilder builder = CreatePlacementGroupRequest.builder();
        builder.name(name);
        builder.type(PlacementGroupType.spread);
        PlacementGroupResponse placementGroup = hetznerCloudAPI.createPlacementGroup(builder.build());
        return placementGroup.getPlacementGroup();
    }

    public PlacementGroup findPlacementGroup(String name) {
        PlacementGroupsResponse placementGroups = hetznerCloudAPI.getPlacementGroups();
        Optional<PlacementGroup> first = placementGroups.getPlacementGroups().stream().filter(placementGroup -> placementGroup.getName().equals(name)).findFirst();
        return first.orElse(null);
    }

    public Server findServer(String serverName) {
        ServersResponse server = hetznerCloudAPI.getServer(serverName);
        List<Server> servers = server.getServers();
        if (CollectionUtils.isEmpty(servers)) {
            return null;
        }
        return servers.get(0);
    }

    public Server createServer(
            String clusterName,
            String instanceType,
            String serverName,
            String image,
            List<String> additionalPackages,
            List<String> masterPostCreateCommands,
            Firewall firewall,
            Network network,
            SSHKey sshKey,
            PlacementGroup placementGroup,
            boolean enablePublicNetIpv4,
            boolean enablePublicNetIpv6,
            String location,
            String snapshotOs, int sshPort, String role, boolean debug) {
        CreateServerRequest.CreateServerRequestBuilder builder = CreateServerRequest
                .builder()
                .name(serverName)
                .image(image)
                .startAfterCreate(true)
                .location(location)
                .serverType(instanceType)
                .firewalls(List.of(CreateServerRequestFirewall.builder().firewallId(firewall.getId()).build()))
                .network(network.getId())
                .placementGroup(placementGroup.getId())
                .label("cluster", clusterName)
                .label("role", role)
                .userData(cloudInit(sshPort, snapshotOs, additionalPackages, masterPostCreateCommands, List.of(), debug))
                .sshKeys(Collections.singletonList(sshKey.getId()))
                .publicNet(ServerPublicNetRequest.builder()
                        .enableIPv4(enablePublicNetIpv4)
                        .enableIPv6(enablePublicNetIpv6)
                        .build());
        hetznerCloudAPI.createServer(builder.build());
        return waitForServerCreation(serverName);
    }

    private Server waitForServerCreation(String serverName) {
        // Todo: wait for server to start up also
        long tic = System.currentTimeMillis();
        while (true) {
            Server server = findServer(serverName);
            if (CollectionUtils.isNotEmpty(server.getPrivateNet()) &&
                    StringUtils.isNotBlank(server.getPrivateNet().get(0).getIp())) {
                return server;
            }
            sleep(2000);
            long tac = System.currentTimeMillis();
            // Ideally shouldn't take so long to setup server
            if ((tac - tic) > TimeUnit.MINUTES.toMillis(10)) {
                return server;
            }
        }
    }


    public static String cloudInit(int sshPort, String snapshotOs, List<String> additionalPackages, List<String> additionalPostCreateCommands, List<String> finalCommands, boolean debug) {
        Map<String, Object> data = new HashMap<>();
        data.put("ssh_port", Integer.toString(sshPort));
        data.put("eth1_str", eth1(snapshotOs));
        data.put("growpart_str", growpart(snapshotOs));
        data.put("packages_str", generatePackagesStr(snapshotOs, additionalPackages));
        data.put("post_create_commands_str", generatePostCreateCommandsStr(snapshotOs, additionalPostCreateCommands, finalCommands));
        String cloudInit = TemplateUtil.renderTemplate(TemplateUtil.CLOUD_INIT_YAML_PATH, data);
        if (debug){
            System.out.println(cloudInit);
        }
        return cloudInit;
    }

    public static String growpart(String snapshotOs) {
        return "microos".equals(snapshotOs) ?
                "growpart:\n  devices: [\"/var\"]\n" : "";
    }

    public static String eth1(String snapshotOs) {
        return "microos".equals(snapshotOs) ?
                "- content: |\n    BOOTPROTO='dhcp'\n    STARTMODE='auto'\n  path: /etc/sysconfig/network/ifcfg-eth1\n" : "";
    }

    private static List<String> mandatoryPostCreateCommands() {
        List<String> commands = new ArrayList<>();
        commands.add("hostnamectl set-hostname $(curl http://169.254.169.254/hetzner/v1/metadata/hostname)");
        commands.add("update-crypto-policies --set DEFAULT:SHA1 || true");
        return commands;
    }

    private static List<String> microosCommands() {
        List<String> commands = new ArrayList<>();
        commands.add("btrfs filesystem resize max /var");
        commands.add("sed -i 's/NETCONFIG_DNS_STATIC_SERVERS=\"\"/NETCONFIG_DNS_STATIC_SERVERS=\"1.1.1.1 1.0.0.1\"/g' /etc/sysconfig/network/config");
        commands.add("sed -i 's/#SystemMaxUse=/SystemMaxUse=3G/g' /etc/systemd/journald.conf");
        commands.add("sed -i 's/#MaxRetentionSec=/MaxRetentionSec=1week/g' /etc/systemd/journald.conf");
        commands.add("sed -i 's/NUMBER_LIMIT=\"2-10\"/NUMBER_LIMIT=\"4\"/g' /etc/snapper/configs/root");
        commands.add("sed -i 's/NUMBER_LIMIT_IMPORTANT=\"4-10\"/NUMBER_LIMIT_IMPORTANT=\"3\"/g' /etc/snapper/configs/root");
        commands.add("sed -i 's/NETCONFIG_NIS_SETDOMAINNAME=\"yes\"/NETCONFIG_NIS_SETDOMAINNAME=\"no\"/g' /etc/sysconfig/network/config");
        commands.add("sed -i 's/DHCLIENT_SET_HOSTNAME=\"yes\"/DHCLIENT_SET_HOSTNAME=\"no\"/g' /etc/sysconfig/network/dhcp");
        return commands;
    }

    public static String generatePostCreateCommandsStr(String snapshotOs, List<String> additionalPostCreateCommands, List<String> finalCommands) {
        List<String> postCreateCommands = new ArrayList<>(mandatoryPostCreateCommands());

        if ("microos".equals(snapshotOs)) {
            postCreateCommands.addAll(microosCommands());
        }

        postCreateCommands.addAll(additionalPostCreateCommands);
        postCreateCommands.addAll(finalCommands);

        return postCreateCommands.stream()
                .map(command -> "- " + command)
                .collect(Collectors.joining("\n"));
    }

    public static String generatePackagesStr(String snapshotOs, List<String> additionalPackages) {
        List<String> packages = new ArrayList<>();
        packages.add("fail2ban");
        packages.add("microos".equals(snapshotOs) ? "wireguard-tools" : "wireguard");
        packages.addAll(additionalPackages);
        return packages.stream()
                .map(packageName -> "'" + packageName + "'")
                .collect(Collectors.joining(", "));
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

    public LoadBalancer findLoadBalancer(String loadBalancerName) {
        LoadBalancersResponse loadBalancers = hetznerCloudAPI.getLoadBalancers();
        Optional<LoadBalancer> first = loadBalancers.getLoadBalancers().stream().filter(loadBalancer -> loadBalancer.getName().equals(loadBalancerName)).findFirst();
        return first.orElse(null);
    }
}
