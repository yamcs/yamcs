package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;

/**
 * Receives telemetry packets via UDP. One UDP datagram = one TM packet.
 * <p>
 * Options:
 * <ul>
 * <li>{@code port} - the UDP port to listen to</li>
 * <li>{@code maxLength} - the maximum length of the datagram (and thus the TM packet length + initialBytesToStrip). If
 * a datagram longer than this size will be received, it will be truncated. Default: 1500 (bytes)</li>
 * <li>{@code initialBytesToStrip} - if configured, skip that number of bytes from the beginning of the datagram.
 * Default: 0</li>
 * 
 * </ul>
 */
public class UdpTmDataLink extends AbstractTmDataLink implements Runnable {
    protected volatile long invalidDatagramCount = 0;

    protected DatagramSocket tmSocket;
    protected int port;

    static final int MAX_LENGTH = 1500;
    protected int maxLength;
    protected int initialBytesToStrip;
    protected DatagramPacket datagram;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("maxLength", OptionType.INTEGER).withDefault(MAX_LENGTH);
        spec.addOption("initialBytesToStrip", OptionType.INTEGER).withDefault(0);
        return spec;
    }

    /**
     * Creates a new UDP TM Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        port = config.getInt("port");
        maxLength = config.getInt("maxLength", MAX_LENGTH);
        initialBytesToStrip = config.getInt("initialBytesToStrip", 0);
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            try {
                doEnable();
            } catch (SocketException e) {
                notifyFailed(e);
                return;
            }
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        doDisable();
        notifyStopped();
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            TmPacket tmpkt = getNextPacket();
            if (tmpkt != null) {
                processPacket(tmpkt);
            }
        }
    }

    /**
     * 
     * Called to retrieve the next packet. It blocks in readining on the multicast socket
     * 
     * @return anything that looks as a valid packet, just the size is taken into account to decide if it's valid or not
     */
    public TmPacket getNextPacket() {
        byte[] packet = null;

        while (isRunning()) {
            try {

                tmSocket.receive(datagram);
                int pktLength = datagram.getLength() - initialBytesToStrip;

                if (pktLength <= 0) {
                    log.warn("received datagram of size {} <= {} (initialBytesToStrip); ignored.",
                            datagram.getLength(), initialBytesToStrip);
                    invalidDatagramCount++;
                    continue;
                }

                updateStats(datagram.getLength());
                packet = new byte[pktLength];
                System.arraycopy(datagram.getData(), datagram.getOffset() + initialBytesToStrip, packet, 0, pktLength);
                break;
            } catch (IOException e) {
                if (!isRunning() || isDisabled()) {// the shutdown or disable will close the socket and that will
                                                   // generate an exception
                    // which we ignore here
                    return null;
                }
                log.warn("exception thrown when reading from the UDP socket at port {}", port, e);
            }
        }

        if (packet != null) {
            TmPacket tmPacket = new TmPacket(timeService.getMissionTime(), packet);
            tmPacket.setEarthReceptionTime(timeService.getHresMissionTime());
            return packetPreprocessor.process(tmPacket);
        } else {
            return null;
        }
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return "DISABLED (should receive on " + port + ")";
        } else {
            return "OK, receiving on " + port;
        }
    }

    @Override
    public Map<String, Object> getExtraInfo() {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("Valid datagrams", packetCount.get());
        extra.put("Invalid datagrams", invalidDatagramCount);
        return extra;
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        invalidDatagramCount = 0;
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void doDisable() {
        if (tmSocket != null) {
            tmSocket.close();
            tmSocket = null;
        }
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     * 
     * @throws SocketException
     */
    @Override
    public void doEnable() throws SocketException {
        tmSocket = new DatagramSocket(port);
        Thread thread = new Thread(this);
        thread.setName(getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
