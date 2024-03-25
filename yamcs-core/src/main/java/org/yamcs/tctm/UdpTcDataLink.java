package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
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

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
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
        if (isDisabled()) {
            return String.format("DISABLED (should send to %s:%d)", host, port);
        } else {
            return String.format("OK, sending to %s:%d", host, port);
        }
    }

    @Override
    public void shutDown() {
        socket.close();
    }

    @Override
    public void uplinkCommand(PreparedCommand pc) throws IOException {
        byte[] binary = postprocess(pc);
        if (binary == null) {
            return;
        }

        DatagramPacket packet = new DatagramPacket(binary, binary.length, address, port);
        socket.send(packet);
        dataOut(1, binary.length);
        ackCommand(pc.getCommandId());
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
