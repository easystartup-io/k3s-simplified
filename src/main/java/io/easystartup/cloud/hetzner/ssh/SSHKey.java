package io.easystartup.cloud.hetzner.ssh;

import io.easystartup.cloud.hetzner.HetznerClient;
import me.tomsdevsn.hetznercloud.HetznerCloudAPI;
import me.tomsdevsn.hetznercloud.objects.request.CreateSSHKeyRequest;
import me.tomsdevsn.hetznercloud.objects.response.SSHKeysResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/*
 * @author indianBond
 */
public class SSHKey {
    private final HetznerCloudAPI hetznerCloudAPI;

    public SSHKey(HetznerClient hetznerClient) {
        this.hetznerCloudAPI = hetznerClient.getHetznerCloudAPI();
    }

    public me.tomsdevsn.hetznercloud.objects.general.SSHKey create(String clusterName, String publicSSHKeyPath) {
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

    public me.tomsdevsn.hetznercloud.objects.general.SSHKey find(String publicSSHKeyPath) {
        try {
            String fingerPrint = calculateFingerprint(publicSSHKeyPath);
            SSHKeysResponse sshKey = hetznerCloudAPI.getSSHKeyByFingerprint(fingerPrint);
            return sshKey.getSshKeys().stream().findFirst().orElse(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}
