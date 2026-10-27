package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

import com.google.common.util.concurrent.RateLimiter;

/** UDP egress for composed frame links. */
public class UdpTcFrameTransport implements TcFrameTransport {
    private String host;
    private int port;
    private InetAddress address;
    private DatagramSocket socket;
    private RateLimiter rateLimiter;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("frameMaxRate", OptionType.FLOAT);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        host = args.getString("host");
        port = args.getInt("port");
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Cannot resolve host '" + host + "'", e);
        }
        if (args.containsKey("frameMaxRate")) {
            rateLimiter = RateLimiter.create(args.getDouble("frameMaxRate"), 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void start() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket();
        }
    }

    @Override
    public synchronized void stop() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (rateLimiter != null) {
            rateLimiter.acquire();
        }
        DatagramSocket current;
        synchronized (this) {
            current = socket;
        }
        if (current == null || current.isClosed()) {
            throw new IOException("UDP frame transport is not started");
        }
        current.send(new DatagramPacket(data, data.length, address, port));
    }

    @Override
    public String getDetailedStatus() {
        return "sending to " + host + ":" + port;
    }
}
