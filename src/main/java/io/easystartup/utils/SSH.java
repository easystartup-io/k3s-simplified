package io.easystartup.utils;

import com.jcraft.jsch.*;
import me.tomsdevsn.hetznercloud.objects.general.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        String host = getHostIPAdress(server);
        JSch jsch = new JSch();
        if (!isSSHAgent) {
            try {
                jsch.addIdentity(privateKeyPath, publicKeyPath);
            } catch (JSchException e) {
                throw new RuntimeException(e);
            }
        }
        Session session = null;
        try {
            session = jsch.getSession("root", host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.setInputStream(null);

            InputStream in = channel.getInputStream();
            channel.connect();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println(line);
                }
            }

            channel.disconnect();
            session.disconnect();

            return output.toString().trim();
        } catch (JSchException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getHostIPAdress(Server server) {
        if (server.getPublicNet() != null && server.getPublicNet().getIpv4() != null && isNotBlank(server.getPublicNet().getIpv4().getIp())) {
            return server.getPublicNet().getIpv4().getIp();
        }
        return server.getPrivateNet().getFirst().getIp();
    }
}
