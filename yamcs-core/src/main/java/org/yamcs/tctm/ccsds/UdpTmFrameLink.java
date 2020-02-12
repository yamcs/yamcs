package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Receives telemetry fames via UDP. One UDP datagram = one TM frame.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractTmFrameLink implements Runnable {
    private volatile int invalidDatagramCount = 0;

    private DatagramSocket tmSocket;
    private int port;

    final DatagramPacket datagram;
    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    Thread thread;

    /**
     * Creates a new UDP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public UdpTmFrameLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        super(instance, name, config);
        port = config.getInt("port");
        int maxLength = frameHandler.getMaxFrameSize();
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            try {
                tmSocket = new DatagramSocket(port);
                new Thread(this).start();
            } catch (SocketException e) {
                notifyFailed(e);
            }
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        tmSocket.close();
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
                int length = datagram.getLength();
                if (length < frameHandler.getMinFrameSize()) {
                    eventProducer.sendWarning("Error processing frame: size " + length
                            + " shorter than minimum allowed " + frameHandler.getMinFrameSize());
                    continue;
                }
                if (length > frameHandler.getMaxFrameSize()) {
                    eventProducer.sendWarning("Error processing frame: size " + length + " longer than maximum allowed "
                            + frameHandler.getMaxFrameSize());
                    continue;
                }
                frameCount++;

                frameHandler.handleFrame(TimeEncoding.getWallclockTime(), datagram.getData(), datagram.getOffset(),
                        length);
            } catch (IOException e) {
                if (isDisabled()) {
                    break;
                }
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            } catch (TcTmException e) {
                eventProducer.sendWarning("Error processing frame: " + e.toString());
            } catch (Exception e) {
                log.error("Error processing frame", e);
            }
        }
    }

    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return "DISABLED";
        } else {
            return String.format("OK (%s) %nValid datagrams received: %d%nInvalid datagrams received: %d",
                    port, frameCount, invalidDatagramCount);
        }
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
        new Thread(this).start();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
