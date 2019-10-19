package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.ACK_SENT_CNAME_PREFIX;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Collection;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import com.google.common.util.concurrent.AbstractService;

/**
 * Base implementation for a TC data link that initialises a post processor and provides a queing and rate limiting
 * function.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbstractTcDataLink extends AbstractService
        implements TcDataLink, SystemParametersProducer {

    protected CommandHistoryPublisher commandHistoryPublisher;
    SelectionKey selectionKey;

    protected volatile boolean disabled = false;

    protected volatile long dataCount;

    private String sv_linkStatus_id, sp_dataCount_id;

    protected SystemParametersCollector sysParamCollector;
    protected final Log log;
    protected final String yamcsInstance;
    protected final String name;
    TimeService timeService;

    protected CommandPostprocessor cmdPostProcessor;
    final YConfiguration config;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);

    protected long housekeepingInterval = 10000;
    private AggregatedDataLink parent = null;
    
   
    protected boolean failCommandOnDisabled;
    
    public AbstractTcDataLink(String yamcsInstance, String linkName, YConfiguration config)
            throws ConfigurationException {
        log = new Log(getClass(), yamcsInstance);
        log.setContext(linkName);
        this.yamcsInstance = yamcsInstance;
        this.name = linkName;
        this.config = config;
        timeService = YamcsServer.getTimeService(yamcsInstance);
        
        failCommandOnDisabled = config.getBoolean("failCommandOnDisabled", false);
        initPostprocessor(yamcsInstance, config);
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
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

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
    }

    public String getLinkName() {
        return name;
    }

    @Override
    public Status getLinkStatus() {
        if(disabled) {
            return Status.DISABLED;
        } else {
            return Status.OK;
        }
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
    @Override
    public AggregatedDataLink getParent() {
        return parent ;
    }

    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
    }
    
    /**Send to command history the failed command */
    protected void failedCommand(CommandId commandId, String reason) {
        commandHistoryPublisher.publishWithTime(commandId, ACK_SENT_CNAME_PREFIX,
                getCurrentTime(), "NOK");
        commandHistoryPublisher.commandFailed(commandId,  reason);
    }
}
