package org.yamcs.tctm;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Base implementation for a TC data link that initialises a post processor and provides a queing and rate limiting
 * function.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbstractTcDataLink extends AbstractExecutionThreadService
        implements TcDataLink, SystemParametersProducer {

    protected CommandHistoryPublisher commandHistoryListener;
    SelectionKey selectionKey;

    protected volatile boolean disabled = false;

    protected BlockingQueue<PreparedCommand> commandQueue;
    RateLimiter rateLimiter;

    protected volatile long dataCount;

    private String sv_linkStatus_id, sp_dataCount_id;

    protected SystemParametersCollector sysParamCollector;
    protected final Log log;
    private final String yamcsInstance;
    private final String name;
    TimeService timeService;

    CommandPostprocessor cmdPostProcessor;
    final YConfiguration config;
    long initialDelay;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);

    protected long housekeepingInterval = 10000;
    
    public AbstractTcDataLink(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        log = new Log(getClass(), yamcsInstance);
        log.setContext(name);
        this.yamcsInstance = yamcsInstance;
        this.name = name;
        this.config = config;
        if (config.containsKey("tcQueueSize")) {
            commandQueue = new LinkedBlockingQueue<>(config.getInt("tcQueueSize"));
        } else {
            commandQueue = new LinkedBlockingQueue<>();
        }
        if (config.containsKey("tcMaxRate")) {
            rateLimiter = RateLimiter.create(config.getInt("tcMaxRate"));
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
        initialDelay = config.getLong("initialDelay", 0);
        initPostprocessor(yamcsInstance, config);
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
    }

    @Override
    protected void startUp() throws Exception {
        setupSysVariables();
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
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return dataCount;
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
        dataCount = 0;
    }
    
    public void run() throws Exception {
        if (initialDelay > 0) {
            Thread.sleep(initialDelay);
        }

        while (isRunning()) {
            try {
                PreparedCommand pc = commandQueue.poll(housekeepingInterval, TimeUnit.MILLISECONDS);
                if(pc==null) {
                   doHousekeeping();
                   continue;
                }
                if (pc == SIGNAL_QUIT) {
                    return;
                }

                if (rateLimiter != null) {
                    rateLimiter.acquire();
                }
                uplinkCommand(pc);
            } catch (Exception e) {
                log.error("Error when sending command: ", e);
                throw e;
            }
        }
    }

    protected void doHousekeeping() {
    }

    protected abstract void uplinkCommand(PreparedCommand pc) throws IOException;

    protected void triggerShutdown() {
        commandQueue.clear();
        commandQueue.offer(SIGNAL_QUIT);
    }
    

}
