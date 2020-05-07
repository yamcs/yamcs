package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;

/**
 * Sends raw packets on UDP socket.
 * 
 * @author nm
 *
 */
public class UdpTcDataLink extends AbstractThreadedTcDataLink {

    protected DatagramSocket socket;
    protected String host;
    protected int port;
    InetAddress address;

    public void init(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
    }

    @Override
    protected void startUp() throws SocketException, UnknownHostException {
        address = InetAddress.getByName(host);
        socket = new DatagramSocket();
    }

    @Override
    public String getDetailedStatus() {
        return String.format("OK, connected to %s:%d", host, port);
    }

    @Override
    public void shutDown() {
        socket.close();
    }

    public void uplinkCommand(PreparedCommand pc) throws IOException {
        byte[] binary = cmdPostProcessor.process(pc);
        DatagramPacket packet = new DatagramPacket(binary, binary.length, address, port);
        socket.send(packet);
        dataCount++;
        ackCommand(pc.getCommandId());
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
