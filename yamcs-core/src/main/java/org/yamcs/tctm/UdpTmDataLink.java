package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.utils.TimeEncoding;

/**
 * Receives telemetry packets via UDP. One UDP datagram = one TM packet.
 * 
 * Keeps simple statistics about the number of datagram received and the number of too short datagrams
 * 
 * @author nm
 *
 */
public class UdpTmDataLink extends AbstractTmDataLink {
    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;
    private volatile boolean disabled = false;

    private volatile boolean quitting = false;
    private DatagramSocket tmSocket;
    private int port = 31002;

    private TmSink tmSink;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    final static int MAX_LENGTH = 1500;
    final DatagramPacket datagram;
    final int maxLength;
    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;

    /**
     * Creates a new UDP TM Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public UdpTmDataLink(String instance, String name, Map<String, Object> args) throws ConfigurationException {
        port = YConfiguration.getInt(args, "port");
        maxLength = YConfiguration.getInt(args, "maxLength", MAX_LENGTH);
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
        initPreprocessor(instance, args);
    }

    @Override
    public void startUp() throws IOException {
        tmSocket = new DatagramSocket(port);
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public void run() {
        while (isRunning()) {
            PacketWithTime pwrt = getNextPacket();
            tmSink.processPacket(pwrt);
            while (disabled) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * 
     * Called to retrieve the next packet. It blocks in readining on the multicast socket
     * 
     * @return anything that looks as a valid packet, just the size is taken into account to decide if it's valid or not
     */
    public PacketWithTime getNextPacket() {
        ByteBuffer packet = null;

        long rectime = TimeEncoding.INVALID_INSTANT;
        while (isRunning()) {
            try {
                tmSocket.receive(datagram);
                if (datagram.getLength() < 16) { // 6 for the primary CCSDS header plus 10 for secondary CCSDS header
                    log.warn("Incomplete packet received on the multicast, discarded: {}", datagram);
                    continue;
                }

                byte[] data = datagram.getData();
                int offset = datagram.getOffset();

                // the time sent by TMR is not really GPS, it's the unix local computer time shifted to GPS epoch
                int pktLength = 7 + ((data[4 + offset] & 0xFF) << 8) + (data[5 + offset] & 0xFF);
                if ((pktLength < 16) || pktLength > maxLength) {
                    invalidDatagramCount++;
                    log.warn(
                            "Invalid packet received on the multicast, pktLength: {}. Expecting minimum 16 bytes and maximum {} bytes",
                            pktLength, maxLength);
                    continue;
                }

                if (datagram.getLength() < pktLength) {
                    invalidDatagramCount++;
                    log.warn("Incomplete packet received on the multicast. expected {}, received: {}", pktLength,
                            datagram.getLength());
                    continue;
                }
                validDatagramCount++;
                packet = ByteBuffer.allocate(pktLength);
                packet.put(data, offset, pktLength);
                break;
            } catch (IOException e) {
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            }
        }

        if (packet != null) {
            return packetPreprocessor.process(packet.array());
        } else {
            return null;
        }

    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
    }

    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return "DISABLED";
        } else {
            return String.format("OK (%s) %nValid datagrams received: %d%nInvalid datagrams received: %d",
                    port, validDatagramCount, invalidDatagramCount);
        }
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void disable() {
        disabled = true;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return validDatagramCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }
}
