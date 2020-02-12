package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;

/**
 * Receives telemetry packets via UDP. One UDP datagram = one TM packet.
 * 
 * Keeps simple statistics about the number of datagram received and the number of too short datagrams
 * 
 * @author nm
 *
 */
public class UdpTmDataLink extends AbstractTmDataLink implements Runnable {
    private volatile int invalidDatagramCount = 0;

    private DatagramSocket tmSocket;
    private int port;

    final static int MAX_LENGTH = 1500;
    final DatagramPacket datagram;
    final int maxLength;

    /**
     * Creates a new UDP TM Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public UdpTmDataLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        super(instance, name, config);
        port = config.getInt("port");
        maxLength = config.getInt("maxLength", MAX_LENGTH);
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
        initPreprocessor(instance, config);
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
            TmPacket pwrt = getNextPacket();
            if (pwrt != null) {
                tmSink.processPacket(pwrt);
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
        ByteBuffer packet = null;

        while (isRunning()) {
            try {
                tmSocket.receive(datagram);
                updateStats(datagram.getLength());
                packet = ByteBuffer.allocate(datagram.getLength());
                packet.put(datagram.getData(), datagram.getOffset(), datagram.getLength());
                break;
            } catch (IOException e) {
                if (!isRunning() || isDisabled()) {// the shutdown or disable will close the socket and that will generate an exception
                                   // which we ignore here
                    return null;
                }
                log.warn("exception thrown when reading from the UDP socket at port {}", port, e);
            }
        }

        if (packet != null) {
            return packetPreprocessor.process(new TmPacket(timeService.getMissionTime(), packet.array()));
        } else {
            return null;
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
                    port, packetCount, invalidDatagramCount);
        }
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void doDisable() {
        tmSocket.close();
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     * @throws SocketException 
     */
    @Override
    public void doEnable() throws SocketException {
        tmSocket = new DatagramSocket(port);
        new Thread(this).start();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
