package org.yamcs.tse;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import org.yamcs.YConfiguration;

public class UdpDriver extends InstrumentDriver {

    private int sourcePort;
    private String host;
    private int port;

    private DatagramSocket socket;
    private InetAddress address;
    private int maxLength;

    @Override
    public void init(String name, YConfiguration config) {
        super.init(name, config);
        sourcePort = config.getInt("sourcePort", 0);
        host = config.getString("host");
        port = config.getInt("port");
        maxLength = config.getInt("maxLength", 1500);
    }

    @Override
    public void connect() throws IOException {
        socket = new DatagramSocket(sourcePort);
        address = InetAddress.getByName(host);
    }

    @Override
    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public void write(byte[] cmd) throws IOException {
        var packet = new DatagramPacket(cmd, cmd.length, address, port);
        socket.send(packet);
    }

    @Override
    public void readAvailable(ResponseBuffer buffer, int timeout) throws IOException {
        // Block receive on a small timeout, this method may be called
        // many times for a single response.
        socket.setSoTimeout(timeout);

        var receivePacket = new DatagramPacket(new byte[maxLength], maxLength);
        try {
            socket.receive(receivePacket);
            buffer.append(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
        } catch (SocketTimeoutException e) {
            // Ignore, the real timeout is managed by the caller.
        }
    }

    @Override
    public String getDefaultRequestTermination() {
        return null;
    }

    @Override
    public boolean isFragmented() {
        return false; // Assume one response message per datagram
    }
}
