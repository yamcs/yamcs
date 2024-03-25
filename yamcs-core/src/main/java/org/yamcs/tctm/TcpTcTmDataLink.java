package org.yamcs.tctm;


import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.YObjectLoader;

public class TcpTcTmDataLink extends AbstractTcTmParamLink implements Runnable {
    protected Socket tmSocket;
    protected String host;
    protected int port;
    protected long initialDelay;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialDelay", OptionType.INTEGER);
        spec.addOption("packetInputStreamClassName", OptionType.STRING);
        spec.addOption("packetInputStreamArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        host = config.getString("host");
        port = config.getInt("port");

        initialDelay = config.getLong("initialDelay", -1);
        // Input stream defaults to GenericPacketInputStream
        if (config.containsKey("packetInputStreamClassName")) {
            packetInputStreamClassName = config.getString("packetInputStreamClassName");
            packetInputStreamArgs = config.getConfigOrEmpty("packetInputStreamArgs");
        } else {
            packetInputStreamClassName = GenericPacketInputStream.class.getName();
            HashMap<String, Object> m = new HashMap<>();
            m.put("maxPacketLength", 1000);
            m.put("lengthFieldOffset", 4);
            m.put("lengthFieldLength", 2);
            m.put("lengthAdjustment", 7);
            m.put("initialBytesToStrip", 0);
            packetInputStreamArgs = YConfiguration.wrap(m);
        }      
    }

    protected synchronized void checkAndOpenSocket() throws IOException {
        if (tmSocket != null) {
            return;
        }
        InetAddress address = InetAddress.getByName(host);
        tmSocket = new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address, port), 1000);
        try {
            packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
            outputStream = tmSocket.getOutputStream();
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput: " + e);
            try {
                tmSocket.close();
            } catch (IOException e2) {
            }
            tmSocket = null;
            outputStream = null;
            packetInputStream = null;
            throw e;
        }
        packetInputStream.init(tmSocket.getInputStream(), packetInputStreamArgs);
        log.info("Link established to {}:{}", host, port);
    }

    protected synchronized boolean isSocketOpen() {
        return tmSocket != null;
    }

    protected synchronized void sendBuffer(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException(String.format("No connection to %s:%d", host, port));
        }
        outputStream.write(data);
    }

    protected synchronized void closeSocket() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
            }
            tmSocket = null;
            outputStream = null;
            packetInputStream = null;
        }
    }

    @Override
    public void run() {
        if (initialDelay > 0) {
            try {
                Thread.sleep(initialDelay);
                initialDelay = -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (isRunningAndEnabled()) {
            TmPacket tmpkt = getNextPacket();
            if (tmpkt == null) {
                break;
            }
            processPacket(tmpkt);
        }
    }

    public TmPacket getNextPacket() {
        TmPacket pwt = null;
        while (isRunningAndEnabled()) {
            try {
                checkAndOpenSocket();
                byte[] packet = packetInputStream.readPacket();
                dataIn(1, packet.length);
                TmPacket pkt = new TmPacket(timeService.getMissionTime(), packet);
                pkt.setEarthReceptionTime(timeService.getHresMissionTime());
                pwt = packetPreprocessor.process(pkt);
                if (pwt != null) {
                    break;
                }
            } catch (EOFException e) {
                log.warn("TM Connection closed");
                closeSocket();
            } catch (IOException e) {
                if (isRunningAndEnabled()) {
                    String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
                    log.info("Cannot open or read TM socket {}:{} {}'. Retrying in 10s", host, port, exc);
                }
                closeSocket();
                for (int i = 0; i < 10; i++) {
                    if (!isRunningAndEnabled()) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            } catch (PacketTooLongException e) {
                log.warn(e.toString());
                closeSocket();
            }
        }
        return pwt;
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        byte[] binary = postprocess(pc);

        try {
            sendBuffer(binary);
            dataOutCount.getAndIncrement();
            ackCommand(pc.getCommandId());
            return true;
        } catch (IOException e) {
            String reason = String.format("Error writing to TC socket to %s:%d; %s", host, port, e.toString());
            log.warn(reason);
            failedCommand(pc.getCommandId(), reason);
            return true;
        }
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            Thread thread = new Thread(this);
            thread.setName(getClass().getSimpleName() + "-" + linkName);
            thread.start();
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        closeSocket();
        notifyStopped();
    }

    @Override
    public void doDisable() {
        closeSocket();
    }

    @Override
    public void doEnable() {
        Thread thread = new Thread(this);
        thread.setName(getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (isSocketOpen()) {
            return String.format("OK, connected to %s:%d", host, port);
        } else {
            return String.format("Not connected to %s:%d", host, port);
        }
    }

    @Override
    protected Status connectionStatus() {
        return !isSocketOpen() ? Status.UNAVAIL : Status.OK;
    }
}
