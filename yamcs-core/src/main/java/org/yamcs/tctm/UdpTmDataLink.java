package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.logging.Log;

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

    private DatagramSocket tmSocket;
    private int port;

    private TmSink tmSink;

    private Log log;
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
    public UdpTmDataLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        super(instance, name, config);
        log = new Log(getClass(), instance);
        log.setContext(name);
        port = config.getInt("port");
        maxLength = config.getInt("maxLength", MAX_LENGTH);
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
        initPreprocessor(instance, config);
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
            if (pwrt != null) {
                tmSink.processPacket(pwrt);
            }
            while (isRunning() && disabled) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void triggerShutdown() {
        tmSocket.close();
    }

    /**
     * 
     * Called to retrieve the next packet. It blocks in readining on the multicast socket
     * 
     * @return anything that looks as a valid packet, just the size is taken into account to decide if it's valid or not
     */
    public PacketWithTime getNextPacket() {
        ByteBuffer packet = null;

        while (isRunning()) {
            try {
                tmSocket.receive(datagram);
                validDatagramCount++;
                packet = ByteBuffer.allocate(datagram.getLength());
                packet.put(datagram.getData(), datagram.getOffset(), datagram.getLength());
                break;
            } catch (IOException e) {
                if (!isRunning()) {// the triggerShutdown will close the socket and that will generate an exception
                                   // which we ignore here
                    return null;
                }
                log.warn("exception thrown when reading from the UDP socket at port {}", port, e);
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

    @Override
    public void resetCounters() {
        validDatagramCount = 0;
    }
}
