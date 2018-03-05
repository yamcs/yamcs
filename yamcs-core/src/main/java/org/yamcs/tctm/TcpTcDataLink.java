package org.yamcs.tctm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.TimeEncoding;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Sends raw packets on Tcp socket.
 * 
 * @author nm
 *
 */
public class TcpTcDataLink extends AbstractService implements Runnable, TcDataLink, SystemParametersProducer {
    protected SocketChannel socketChannel = null;
    protected String host = "whirl";
    protected int port = 10003;
    protected CommandHistoryPublisher commandHistoryListener;
    protected Selector selector;
    SelectionKey selectionKey;
    protected CcsdsSeqAndChecksumFiller seqAndChecksumFiller = new CcsdsSeqAndChecksumFiller();
    protected ScheduledThreadPoolExecutor timer;
    protected volatile boolean disabled = false;
    protected int minimumTcPacketLength = -1; // the minimum size of the CCSDS packets uplinked
    protected BlockingQueue<PreparedCommand> commandQueue;
    RateLimiter rateLimiter;
    
    protected volatile long tcCount;

    private String sv_linkStatus_id, sp_dataCount_id;

    private SystemParametersCollector sysParamCollector;
    protected final Logger log;
    private String yamcsInstance;
    private String name;
    TimeService timeService;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);
    TcDequeueAndSend tcSender;
    
    public TcpTcDataLink(String yamcsInstance, String name, Map<String, Object> config) throws ConfigurationException {
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        this.yamcsInstance = yamcsInstance;
        this.name = name;
        
        configure(config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    public TcpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("tcp").getMap(spec));
    }

    private void configure(Map<String, Object> config) {
        host = YConfiguration.getString(config, "tcHost");
        port = YConfiguration.getInt(config, "tcPort");
        
        minimumTcPacketLength = YConfiguration.getInt(config, "minimumTcPacketLength", -1);
       
        
        if (config.containsKey("tcQueueSize")) {
            commandQueue = new LinkedBlockingQueue<>(YConfiguration.getInt(config, "tcQueueSize"));
        } else {
            commandQueue = new LinkedBlockingQueue<>();
        }
        if (config.containsKey("tcMaxRate")) {
            rateLimiter = RateLimiter.create( YConfiguration.getInt(config, "tcMaxRate"));
        }
        
    }
    
    protected TcpTcDataLink() {
        log = LoggerFactory.getLogger(this.getClass().getName());
    } // dummy constructor which is automatically invoked by subclass constructors

    public TcpTcDataLink(String host, int port) {
        this.host = host;
        this.port = port;
        log = LoggerFactory.getLogger(this.getClass().getName());
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.fromUnixTime(System.currentTimeMillis());
        }
    }

    @Override
    protected void doStart() {
        setupSysVariables();
        this.timer = new ScheduledThreadPoolExecutor(2);
        openSocket();
        tcSender = new TcDequeueAndSend();
        timer.execute(tcSender);
        timer.scheduleAtFixedRate(this, 10L, 10L, TimeUnit.SECONDS);
        notifyStarted();
    }

    /**
     * attempts to open the socket if not alreayd open and returns true if its open at the end of the call 
     * @return
     */
    protected synchronized boolean openSocket() {
        if(isSocketOpen()) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            socketChannel = SocketChannel.open(new InetSocketAddress(address, port));
            socketChannel.configureBlocking(false);
            socketChannel.socket().setKeepAlive(true);
            selector = Selector.open();
            selectionKey = socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            log.info("TC connection established to {}:{}", host, port);
            return true;
        } catch (IOException e) {
            String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
            log.info("Cannot open TC connection to {}:{} '{}'. Retrying in 10s", host, port, exc.toString());
            try {
                socketChannel.close();
            } catch (Exception e1) {
            }
            try {
                selector.close();
            } catch (Exception e1) {
            }
            socketChannel = null;
        }
        return false;
    }

    protected void disconnect() {
        if (socketChannel == null) {
            return;
        }
        try {
            socketChannel.close();
            selector.close();
            socketChannel = null;
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to {}:{} is open", host, port, e);
        }
    }

    /**
     * we check if the socket is open by trying a select on the read part of it
     * 
     * @return
     */
    private boolean isSocketOpen() {
        if (socketChannel == null) {
            return false;
        }
        final ByteBuffer bb = ByteBuffer.allocate(16);
        boolean connected = false;
        try {
            selector.select();
            if (selectionKey.isReadable()) {
                int read = socketChannel.read(bb);
                if (read > 0) {
                    log.info("Data read on the TC socket to {}:{}!! : {}", host, port, bb);
                    connected = true;
                } else if (read < 0) {
                    log.warn("TC socket to {}:{} has been closed", host, port);
                    socketChannel.close();
                    selector.close();
                    socketChannel = null;
                    connected = false;
                }
            } else if (selectionKey.isWritable()) {
                connected = true;
            } else {
                log.warn("The TC socket to {}:{} is neither writable nor readable", host, port);
                connected = false;
            }
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to {}:{} is open:", host, port, e);
            connected = false;
        }
        return connected;
    }

    /**
     * Sends
     */
    @Override
    public void sendTc(PreparedCommand pc) {
        if (disabled) {
            log.warn("TC disabled, ignoring command {}", pc.getCommandId());
            return;
        }
        if (!commandQueue.offer(pc)) {
            log.warn("Cannot put command {} in the queue, because it's full; sending NACK", pc); 
            commandHistoryListener.publishWithTime(pc.getCommandId(), "Acknowledge_Sent", getCurrentTime(), "NOK");
        }
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (isSocketOpen()) {
            return Status.OK;
        } else {
            return Status.UNAVAIL;
        }
    }

    @Override
    public String getDetailedStatus() {
        if (disabled)
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        if (isSocketOpen()) {
            return String.format("OK, connected to %s:%d", host, port);
        } else {
            return String.format("Not connected to %s:%d", host, port);
        }
    }

    @Override
    public void disable() {
        disabled = true;
        if (isRunning()) {
            disconnect();
        }
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void run() {
        if (!isRunning() || disabled) {
            return;
        }
        openSocket();
    }

    @Override
    public void doStop() {
        disconnect();
        commandQueue.clear();
        commandQueue.offer(SIGNAL_QUIT);
        timer.shutdownNow();
        notifyStopped();
    }


    private class TcDequeueAndSend implements Runnable {
        PreparedCommand pc;
      
        @Override
        public void run() {
            while (true) {
                try {
                    pc = commandQueue.take();
                    if(pc==SIGNAL_QUIT) { 
                        break;
                    }
                    
                    if(rateLimiter!=null) {
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
            ByteBuffer bb = null;
            if (pc.getBinary().length < minimumTcPacketLength) { // enforce the minimum packet length
                bb = ByteBuffer.allocate(minimumTcPacketLength);
                bb.put(pc.getBinary());
                bb.putShort(4, (short) (minimumTcPacketLength - 7)); // fix packet length
            } else {
                int checksumIndicator = pc.getBinary()[2] & 0x04;
                if (checksumIndicator == 1) {
                    bb = ByteBuffer.allocate(pc.getBinary().length + 2); // extra slots for check sum
                } else {
                    bb = ByteBuffer.wrap(pc.getBinary());
                }
                bb.putShort(4, (short) (pc.getBinary().length - 7));

            }

            int retries = 5;
            boolean sent = false;
            int seqCount = seqAndChecksumFiller.fill(bb, pc.getCommandId().getGenerationTime());
            commandHistoryListener.publish(pc.getCommandId(), "ccsds-seqcount", seqCount);
            
            bb.rewind();
            while (!sent && (retries > 0)) {              
                if (openSocket()) {
                    try {
                        socketChannel.write(bb);
                        tcCount++;
                        sent = true;
                    } catch (IOException e) {
                        log.warn("Error writing to TC socket to {}:{} : {}", host, port, e.getMessage());
                        try {
                            if (socketChannel.isOpen()) {
                                socketChannel.close();
                            }
                            selector.close();
                            socketChannel = null;
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
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
            if (sent) {
                commandHistoryListener.publishWithTime(pc.getCommandId(), "Acknowledge_Sent", getCurrentTime(), "OK");
            } else {
                commandHistoryListener.publishWithTime(pc.getCommandId(), "Acknowledge_Sent", getCurrentTime(), "NOK");
            }
        }
    }

    @Override
    public long getDataCount() {
        return tcCount;
    }

    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if (sysParamCollector != null) {
            sysParamCollector.registerProducer(this);
            sv_linkStatus_id = sysParamCollector.getNamespace() + "/" + name + "/linkStatus";
            sp_dataCount_id = sysParamCollector.getNamespace() + "/" + name + "/dataCount";

        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters() {
        long time = getCurrentTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(sv_linkStatus_id, time, getLinkStatus().name());
        ParameterValue dataCount = SystemParametersCollector.getPV(sp_dataCount_id, time, getDataCount());
        return Arrays.asList(linkStatus, dataCount);
    }

    public int getMiniminimumTcPacketLength() {
        return minimumTcPacketLength;
    }
}
