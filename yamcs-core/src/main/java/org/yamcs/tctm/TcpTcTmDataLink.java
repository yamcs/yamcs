package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

public class TcpTcTmDataLink extends AbstractTmDataLink implements TcDataLink, Runnable {

    protected CommandHistoryPublisher commandHistoryPublisher;
    protected AtomicLong dataOutCount = new AtomicLong();
    protected CommandPostprocessor cmdPostProcessor;
    private AggregatedDataLink parent = null;

    protected Socket tmSocket;
    protected String host;
    protected int port;
    protected long initialDelay;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;
    PacketInputStream packetInputStream;
    OutputStream outputStream;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialDelay", OptionType.INTEGER);
        spec.addOption("packetInputStreamClassName", OptionType.STRING);
        spec.addOption("packetInputStreamArgs", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("commandPostprocessorClassName", OptionType.STRING);
        spec.addOption("commandPostprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        return super.getSpec();
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

        // Setup tc postprocessor
        initPostprocessor(yamcsInstance, config);
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
                updateStats(packet.length);
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

    protected void initPostprocessor(String instance, YConfiguration config) throws ConfigurationException {
        String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
        YConfiguration commandPostprocessorArgs = null;

        // The GenericCommandPostprocessor class does nothing if there are no arguments, which is what we want.
        if (config != null) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName",
                    GenericCommandPostprocessor.class.getName());
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

        // Instantiate
        try {
            if (commandPostprocessorArgs != null) {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance,
                        commandPostprocessorArgs);
            } else {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the command postprocessor", e);
            throw e;
        }
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (!pc.disablePostprocessing()) {
            binary = cmdPostProcessor.process(pc);
            if (binary == null) {
                log.warn("command postprocessor did not process the command");
                return true;
            }
        }

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
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
    }

    /** Send to command history the failed command */
    protected void failedCommand(CommandId commandId, String reason) {
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = getCurrentTime();
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY, currentTime, AckStatus.NOK, reason);
        commandHistoryPublisher.commandFailed(commandId, currentTime, reason);
    }

    /**
     * send an ack in the command history that the command has been sent out of the link
     * 
     * @param commandId
     */
    protected void ackCommand(CommandId commandId) {
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY, getCurrentTime(), AckStatus.OK);
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
    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        }
        return TimeEncoding.getWallclockTime();
    }

    @Override
    public long getDataOutCount() {
        return dataOutCount.get();
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        dataOutCount.set(0);
    }

    @Override
    public AggregatedDataLink getParent() {
        return parent;
    }

    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
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
