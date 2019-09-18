package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;

/**
 * Sends raw packets on UDP socket.
 * 
 * @author nm
 *
 */
public class UdpTcDataLink extends AbstractTcDataLink {

    protected DatagramSocket socket;
    protected String host;
    protected int port;

    public UdpTcDataLink(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        super(yamcsInstance, name, config);
    }

    public UdpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("udp").getConfig(spec));
    }

    @Override
    protected void startUp() throws SocketException {
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
        InetAddress address = InetAddress.getByName(host);
        DatagramPacket packet = new DatagramPacket(binary, binary.length, address, port);
        socket.send(packet);
        dataCount++;

    }
}
