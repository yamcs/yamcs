package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;

/**
 * Receives telemetry fames via UDP. One UDP datagram = one TM frame.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractTmFrameLink implements Runnable {
    private DatagramSocket tmSocket;
    private int port;

    DatagramPacket datagram;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("port", OptionType.INTEGER);
        return spec;
    }

    /**
     * Creates a new UDP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        port = config.getInt("port");
        int maxLength = frameHandler.getMaxFrameSize();
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            try {
                tmSocket = new DatagramSocket(port);
                Thread thread = new Thread(this);
                thread.setName(getClass().getSimpleName() + "-" + linkName);
                thread.start();
            } catch (SocketException e) {
                notifyFailed(e);
                return;
            }
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        if (tmSocket != null) {
            tmSocket.close();
            tmSocket = null;
        }
        notifyStopped();
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            try {
                tmSocket.receive(datagram);
                if (log.isTraceEnabled()) {
                    log.trace("Received datagram of length {}: {}", datagram.getLength(), StringConverter
                            .arrayToHexString(datagram.getData(), datagram.getOffset(), datagram.getLength(), true));
                }
                dataIn(1, datagram.getLength());
                handleFrame(timeService.getHresMissionTime(), datagram.getData(), datagram.getOffset(),
                        datagram.getLength());

            } catch (IOException e) {
                if (!isRunningAndEnabled()) {
                    break;
                }
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            } catch (Exception e) {
                log.error("Error processing frame", e);
            }
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
        extra.put("Valid frames", validFrameCount.get());
        extra.put("Invalid frames", invalidFrameCount.get());
        return extra;
    }

    @Override
    protected void doDisable() {
        if (tmSocket != null) {
            tmSocket.close();
            tmSocket = null;
        }
    }

    @Override
    protected void doEnable() throws SocketException {
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
