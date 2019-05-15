package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.Service;

/**
 * Sends raw packets on UDP socket.
 * 
 * @author ddw
 *
 */
public class CfdpUdpTcDataLink extends AbstractService implements Runnable, Link, Service {

    String targethost;
    int targetport;

    protected ScheduledThreadPoolExecutor timer;
    protected volatile boolean disabled = false;

    protected BlockingQueue<Packet> commandQueue;
    RateLimiter rateLimiter;

    protected volatile long packetCount;

    private DatagramSocket socket;
    private volatile boolean connected = false;

    protected int localport = 8766;

    protected final Logger log;
    private String name;
    TimeService timeService;
    PacketDequeueAndSend packetSender;

    private YConfiguration config;
    final private String yamcsInstance;
    
    public CfdpUdpTcDataLink(String yamcsInstance, String name, YConfiguration config)
            throws ConfigurationException {
        this.yamcsInstance = yamcsInstance;
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        this.name = name;
        this.config = config;
        configure(yamcsInstance, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    public CfdpUdpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("cfdp-udp").getConfig(spec));
    }

    private void configure(String yamcsInstance, YConfiguration config) {
        targethost = config.getString("targethost");
        targetport = config.getInt("targetport");

        if (config.containsKey("tcQueueSize")) {
            commandQueue = new LinkedBlockingQueue<>(config.getInt("tcQueueSize"));
        } else {
            commandQueue = new LinkedBlockingQueue<>();
        }
        if (config.containsKey("tcMaxRate")) {
            rateLimiter = RateLimiter.create(config.getInt("tcMaxRate"));
        }
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        String streamName = config.getString("stream");
        Stream s = ydb.getStream(streamName);
        if(s==null) {
            notifyFailed(new ConfigurationException("Cannot find stream '"+streamName+"'"));
            return;
        }
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {  }
            
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                sendCfdpPacket(CfdpPacket.fromTuple(tuple));
            }
        });
        this.timer = new ScheduledThreadPoolExecutor(2);
        packetSender = new PacketDequeueAndSend();
        timer.execute(packetSender);
        timer.scheduleAtFixedRate(this, 10L, 10L, TimeUnit.SECONDS);
        notifyStarted();
    }

    @Override
    public void run() {
        if (!isRunning() || disabled) {
            return;
        }
    }

    @Override
    public void doStop() {
        commandQueue.clear();
        timer.shutdownNow();
        notifyStopped();
    }

    public void sendCfdpPacket(CfdpPacket packet) {
        commandQueue.add(packet);
    }

    private class PacketDequeueAndSend implements Runnable {
        Packet packet;

        @Override
        public void run() {
            while (true) {
                try {
                    packet = commandQueue.take();
                    if (rateLimiter != null) {
                        rateLimiter.acquire();
                    }
                    send();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Send command interrupted while waiting for the queue.", e);
                    return;
                } catch (Exception e) {
                    log.error("Error when sending command: ", e);
                    throw e;
                }
            }
        }

        public void send() {
            byte[] binary = packet.toByteArray();
            int retries = 5;
            boolean sent = false;

            ByteBuffer bb = ByteBuffer.wrap(binary);
            bb.rewind();
            while (!sent && (retries > 0)) {
                try {
                    DatagramPacket dp = new DatagramPacket(binary, binary.length, InetAddress.getByName(targethost),
                            targetport);
                    if (!connected) {
                        socket = new DatagramSocket(localport);
                        connected = true;
                    }
                    socket.send(dp);
                    packetCount++;
                    sent = true;
                } catch (IOException e) {
                    log.warn("Error writing to UDP socket to {}:{} : {}", targethost, targetport, e.getMessage());
                }
                retries--;
                if (!sent && (retries > 0)) {
                    try {
                        log.warn("Command not sent, retrying in 2 seconds");
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        log.warn("exception {} thrown when sleeping 2 sec", e.toString());
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    @Override
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return packetCount;
    }

    @Override
    public void resetCounters() {
        packetCount = 0;
    }

    @Override
    public Status getLinkStatus() {
        return isDisabled() ? Status.UNAVAIL : Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        return String.format("UDP link to {}:{} {}", targethost, targetport, isDisabled() ? "DISABLED" : "ENABLED");
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }
}
