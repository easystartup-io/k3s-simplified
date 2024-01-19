package io.easystartup.cloud.hetzner;

import io.easystartup.utils.ConsoleColors;
import io.easystartup.utils.TemplateUtil;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.general.*;
import me.tomsdevsn.hetznercloud.objects.request.CreateServerRequest;
import me.tomsdevsn.hetznercloud.objects.request.CreateServerRequestFirewall;
import me.tomsdevsn.hetznercloud.objects.request.ServerPublicNetRequest;
import me.tomsdevsn.hetznercloud.objects.response.ServerTypesResponse;
import me.tomsdevsn.hetznercloud.objects.response.ServersResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.easystartup.utils.Util.sleep;

/*
 * @author indianBond
 */
public class HetznerClient {

    private final HetznerCloudAPI hetznerCloudAPI;

    public HetznerClient(String token) {
        this.hetznerCloudAPI = new HetznerCloudAPI(token);
    }

    public HetznerCloudAPI getHetznerCloudAPI() {
        return hetznerCloudAPI;
    }

    public List<ServerType> getServerTypes() {
        // todo: replace with paginated fetch all server types in upstream package
        ServerTypesResponse serverTypes = hetznerCloudAPI.getServerTypes();
        return serverTypes.getServerTypes();
    }

    public Server findServer(String serverName) {
        ServersResponse server = hetznerCloudAPI.getServer(serverName);
        List<Server> servers = server.getServers();
        if (CollectionUtils.isEmpty(servers)) {
            return null;
        }
        return servers.get(0);
    }

    public void deleteServer(long id) {
        hetznerCloudAPI.deleteServer(id);
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
            System.out.println(ConsoleColors.YELLOW + cloudInit + ConsoleColors.RESET);
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

        return "- " + String.join("\n  - ", postCreateCommands);
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

}
