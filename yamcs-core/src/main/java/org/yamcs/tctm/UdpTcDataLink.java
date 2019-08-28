package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Sends raw packets on Udp socket.
 * 
 * @author nm
 *
 */
public class UdpTcDataLink extends AbstractService implements TcDataLink, SystemParametersProducer {

    protected DatagramSocket socket;
    protected String host;
    protected int port;
    protected CommandHistoryPublisher commandHistoryListener;
    SelectionKey selectionKey;

    protected ScheduledThreadPoolExecutor timer;
    protected volatile boolean disabled = false;

    protected BlockingQueue<PreparedCommand> commandQueue;
    RateLimiter rateLimiter;

    protected volatile long tcCount;

    private String sv_linkStatus_id, sp_dataCount_id;

    private SystemParametersCollector sysParamCollector;
    protected final Log log;
    private final String yamcsInstance;
    private final String name;
    TimeService timeService;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);
    TcDequeueAndSend tcSender;

    CommandPostprocessor cmdPostProcessor;
    final YConfiguration config;

    public UdpTcDataLink(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        log = new Log(getClass(), yamcsInstance);
        log.setContext(name);
        this.yamcsInstance = yamcsInstance;
        this.name = name;
        this.config = config;
        configure(yamcsInstance, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    public UdpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("udp").getConfig(spec));
    }

    private void configure(String yamcsInstance, YConfiguration config) {
        if (config.containsKey("tcHost")) {// this is when the config is specified in tcp.yaml
            host = config.getString("tcHost");
            port = config.getInt("tcPort");
        } else {
            host = config.getString("host");
            port = config.getInt("port");
        }
        initPostprocessor(yamcsInstance, config);

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
        setupSysVariables();
        this.timer = new ScheduledThreadPoolExecutor(2);
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            notifyFailed(e);
        }
        tcSender = new TcDequeueAndSend();
        timer.execute(tcSender);
        notifyStarted();
    }

    protected void initPostprocessor(String instance, YConfiguration config) {
        String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
        YConfiguration commandPostprocessorArgs = null;

        if (config != null) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName",
                    GenericCommandPostprocessor.class.getName());
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

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
        } catch (IOException e) {
            log.error("Cannot instantiate the command postprocessor", e);
            throw new ConfigurationException(e);
        }
    }

    /**
     * we check if the socket is open by trying a select on the read part of it
     * 
     * @return
     */

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
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
    }

    @Override
    public Status getLinkStatus() {
        return Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        return String.format("OK, connected to %s:%d", host, port);
    }

    @Override
    public void disable() {
        disabled = true;
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
    public void doStop() {
        commandQueue.clear();
        commandQueue.offer(SIGNAL_QUIT);
        timer.shutdownNow();
        socket.close();
        notifyStopped();
    }

    private class TcDequeueAndSend implements Runnable {
        PreparedCommand pc;

        @Override
        public void run() {
            while (true) {
                try {
                    pc = commandQueue.take();
                    if (pc == SIGNAL_QUIT) {
                        break;
                    }

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
                    try {
                        throw e;
                    } catch (Exception e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            }
        }

        public void send() throws IOException {
            byte[] binary = cmdPostProcessor.process(pc);
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(binary, binary.length, address, port);
            socket.send(packet);
            tcCount++;

        }
    }

    @Override
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
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
        ParameterValue dataCount = SystemParametersCollector.getPV(sp_dataCount_id, time, getDataOutCount());
        return Arrays.asList(linkStatus, dataCount);
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void resetCounters() {
        tcCount = 0;
    }
}
