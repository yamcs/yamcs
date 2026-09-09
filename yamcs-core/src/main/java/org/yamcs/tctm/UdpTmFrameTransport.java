package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

/** UDP ingress for composed frame links. One datagram contains one frame. */
public class UdpTmFrameTransport implements TmFrameTransport, Runnable {
    private int port;
    private int initialBytesToStrip;
    private volatile boolean running;
    private DatagramSocket socket;
    private DatagramPacket datagram;
    private Receiver receiver;
    private Thread thread;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialBytesToStrip", OptionType.INTEGER).withDefault(0);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        port = args.getInt("port");
        initialBytesToStrip = args.getInt("initialBytesToStrip", 0);
        if (initialBytesToStrip < 0) {
            throw new ConfigurationException("initialBytesToStrip cannot be negative");
        }
    }

    @Override
    public synchronized void start(int maximumFrameLength, Receiver receiver) throws SocketException {
        if (running) {
            return;
        }
        if (maximumFrameLength <= 0) {
            throw new ConfigurationException("Maximum TM frame transport length must be positive");
        }
        this.receiver = receiver;
        datagram = new DatagramPacket(new byte[maximumFrameLength + initialBytesToStrip],
                maximumFrameLength + initialBytesToStrip);
        socket = new DatagramSocket(port);
        running = true;
        thread = new Thread(this, getClass().getSimpleName() + "-" + port);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                datagram.setLength(datagram.getData().length);
                socket.receive(datagram);
                int frameLength = datagram.getLength() - initialBytesToStrip;
                if (frameLength <= 0) {
                    receiver.onInvalidFrame("Received datagram of size " + datagram.getLength()
                            + " <= initialBytesToStrip " + initialBytesToStrip);
                    continue;
                }
                receiver.onFrame(datagram.getData(), datagram.getOffset() + initialBytesToStrip, frameLength);
            } catch (IOException e) {
                if (running) {
                    fail(e);
                }
            } catch (Exception e) {
                fail(e);
            }
        }
    }

    private void fail(Throwable cause) {
        running = false;
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
        receiver.onFailure(cause);
    }

    @Override
    public String getDetailedStatus() {
        return "receiving on " + port;
    }
}
