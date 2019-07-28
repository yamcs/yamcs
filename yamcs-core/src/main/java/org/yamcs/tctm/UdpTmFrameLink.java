package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.MasterChannelFrameHandler;
import org.yamcs.tctm.ccsds.VirtualChannelHandler;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Receives telemetry fames via UDP. One UDP datagram = one TM frame.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractExecutionThreadService implements AggregatedDataLink {
    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;
    private volatile boolean disabled = false;

    private DatagramSocket tmSocket;
    private int port;

    private Log log;
    final DatagramPacket datagram;
    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    String yamcsInstance;
    String name;
    MasterChannelFrameHandler frameHandler;
    EventProducer eventProducer;
    YConfiguration config;
    List<Link> subLinks;

    /**
     * Creates a new UDP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public UdpTmFrameLink(String instance, String name, YConfiguration args) throws ConfigurationException {
        this.yamcsInstance = instance;
        this.name = name;
        log = new Log(getClass(), instance);
        log.setContext(name);
        port = args.getInt("port");

        frameHandler = new MasterChannelFrameHandler(yamcsInstance, name, args);
        int maxLength = frameHandler.getMaxFrameSize();
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, getClass().getSimpleName(), 10000);
        subLinks = new ArrayList<>();
        for (VirtualChannelHandler vch : frameHandler.getVcHandlers()) {
            if (vch instanceof Link) {
                Link l = (Link) vch;
                subLinks.add(l);
                l.setParent(this);
            }
        }
    }

    @Override
    public void startUp() throws IOException {
        tmSocket = new DatagramSocket(port);
    }

    @Override
    public void shutDown() {
        tmSocket.close();
    }

    @Override
    public void run() {
        while (isRunning()) {
            try {
                tmSocket.receive(datagram);

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
                validDatagramCount++;

                frameHandler.handleFrame(datagram.getData(), datagram.getOffset(), length);
            } catch (IOException e) {
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            } catch (TcTmException e) {
                eventProducer.sendWarning("Error processing frame: " + e.toString());
            } catch (Exception e) {
                log.error("Error processing frame", e);
            }
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

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (state() == State.FAILED) {
            return Status.FAILED;
        }

        return Status.OK;
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

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }
}
