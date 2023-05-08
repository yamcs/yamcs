package org.yamcs.tctm;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class TcpTmDataLink extends AbstractTmDataLink implements Runnable {

    protected Socket tmSocket;
    protected String host;
    protected int port;
    protected long initialDelay;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;
    PacketInputStream packetInputStream;
    Thread thread;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialDelay", OptionType.INTEGER);
        spec.addOption("packetInputStreamClassName", OptionType.STRING)
                .withDefault(CcsdsPacketInputStream.class.getName());
        spec.addOption("packetInputStreamArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
        initialDelay = config.getLong("initialDelay", -1);

        if (config.containsKey("packetInputStreamClassName")) {
            packetInputStreamClassName = config.getString("packetInputStreamClassName");
            packetInputStreamArgs = config.getConfigOrEmpty("packetInputStreamArgs");
        } else {
            packetInputStreamClassName = CcsdsPacketInputStream.class.getName();
            packetInputStreamArgs = YConfiguration.emptyConfig();
        }

    }

    protected void openSocket() throws IOException {
        InetAddress address = InetAddress.getByName(host);
        tmSocket = new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address, port), 1000);
        try {
            packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw e;
        }
        packetInputStream.init(tmSocket.getInputStream(), packetInputStreamArgs);
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        if (thread != null) {
            thread.interrupt();
        }
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
        notifyStopped();
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
                if (tmSocket == null) {
                    openSocket();
                    log.info("Link established to {}:{}", host, port);
                }
                byte[] packet = packetInputStream.readPacket();
                updateStats(packet.length);
                TmPacket pkt = new TmPacket(timeService.getMissionTime(), packet);
                pkt.setEarthReceptionTime(timeService.getHresMissionTime());
                pwt = packetPreprocessor.process(pkt);
                if (pwt != null) {
                    break;
                }
            } catch (IOException e) {
                if (isRunningAndEnabled()) {
                    String msg;
                    if (e instanceof EOFException) {
                        msg = "TM socket connection to " + host + ":" + port + " closed. Reconnecting in 10s.";
                    } else {
                        msg = "Cannot open or read TM socket " + host + ": " + port + ": "
                                + ((e instanceof ConnectException) ? e.getMessage() : e.toString())
                                + ". Retrying in 10 seconds.";
                    }
                    log.warn(msg);
                }
                forceClosedSocket();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (PacketTooLongException e) {
                log.warn(e.toString());
                forceClosedSocket();
            }
        }
        return pwt;
    }

    private void forceClosedSocket() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (Exception e2) {
            }
        }
        tmSocket = null;
    }

    @Override
    public void doDisable() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void doEnable() {
        thread = new Thread(this);
        thread.setName(getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (tmSocket == null) {
            return String.format("Not connected to %s:%d", host, port);
        } else {
            return String.format("OK, connected to %s:%d", host, port);
        }
    }

    @Override
    protected Status connectionStatus() {
        return (tmSocket == null) ? Status.UNAVAIL : Status.OK;
    }
}
