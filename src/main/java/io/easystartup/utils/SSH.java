package io.easystartup.utils;

import me.tomsdevsn.hetznercloud.objects.general.Server;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/*
 * @author indianBond
 */
public class SSH {

    private final String privateKeyPath;
    private final String publicKeyPath;

    public SSH(String privateKeyPath, String publicKeyPath) {
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
    }

    public String ssh(Server server, int port, String command, boolean isSSHAgent) {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((clientSession, socketAddress, publicKey) -> {
            // To skip known hosts check
            return true;
        });
        if (!isSSHAgent) {
            FilePasswordProvider provider = FilePasswordProvider.EMPTY;
            KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
            Collection<KeyPair> keys = null;
            try {
                keys = loader.loadKeyPairs(null, Path.of(privateKeyPath), provider);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }

            for (KeyPair key : keys) {
                client.addPublicKeyIdentity(key);
            }
        }
        client.start();

        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        try (ClientSession session = openSession(client, getHostIPAdress(server), port)) {
            try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream()) {
                session.executeRemoteCommand(command, responseStream, errorStream, StandardCharsets.UTF_8);
                return new String(responseStream.toByteArray()).trim();
            }
        } catch (RemoteException remoteException) {
            if (errorStream != null) {
                System.out.println("Error " + new String(errorStream.toByteArray()));
            }
            return null;
        } catch (IOException e) {
            if (errorStream != null) {
                System.out.println("Error " + new String(errorStream.toByteArray()));
            }
            throw new RuntimeException("SSH command execution failed", e);
        } finally {
            IOUtils.closeQuietly(errorStream);
            client.stop();
        }
    }

    private ClientSession openSession(SshClient client, String host, int port) throws IOException {
        ConnectFuture connectFuture = client.connect("root", host, port);
        connectFuture.await(); // Optionally, you can add a timeout value here
        ClientSession session = connectFuture.getSession();
        session.auth().verify(); // Optionally, you can add a timeout value here
        return session;
    }

    private String getHostIPAdress(Server server) {
        if (server.getPublicNet() != null && server.getPublicNet().getIpv4() != null && isNotBlank(server.getPublicNet().getIpv4().getIp())) {
            return server.getPublicNet().getIpv4().getIp();
        }
        return server.getPrivateNet().getFirst().getIp();
    }
}
