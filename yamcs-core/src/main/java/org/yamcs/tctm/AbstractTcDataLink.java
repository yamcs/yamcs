package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.ACK_SENT_CNAME_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

/**
 * Base implementation for a TC data link that initialises a post processor and provides a queueing and rate limiting
 * function.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbstractTcDataLink extends AbstractLink
        implements TcDataLink, SystemParametersProducer {

    protected CommandHistoryPublisher commandHistoryPublisher;

    protected volatile long dataCount;

    protected String sv_linkStatus_id, sp_dataCount_id;

    protected SystemParametersCollector sysParamCollector;
    TimeService timeService;

    protected CommandPostprocessor cmdPostProcessor;
    static final PreparedCommand SIGNAL_QUIT = new PreparedCommand(new byte[0]);

    protected long housekeepingInterval = 10000;
    private AggregatedDataLink parent = null;
    
   
    public AbstractTcDataLink(String yamcsInstance, String linkName, YConfiguration config)
            throws ConfigurationException {
        super(yamcsInstance, linkName, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
        
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
        return linkName;
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
            sv_linkStatus_id = sysParamCollector.getNamespace() + "/" + linkName + "/linkStatus";
            sp_dataCount_id = sysParamCollector.getNamespace() + "/" + linkName + "/dataCount";

        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }
    }

    @Override
    public List<ParameterValue> getSystemParameters() {
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
        return linkName;
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
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = getCurrentTime();
        commandHistoryPublisher.publishAck(commandId, ACK_SENT_CNAME_PREFIX,
                currentTime, AckStatus.NOK, reason);
        commandHistoryPublisher.commandFailed(commandId,  currentTime, reason);
    }
    
    /**
     * send an ack in the command history that the command has been sent out of the link
     * @param commandId
     */
    protected void ackCommand(CommandId commandId) {
        commandHistoryPublisher.publishAck(commandId, ACK_SENT_CNAME_PREFIX, getCurrentTime(),
                AckStatus.OK);
    }
    public String getYamcsInstance() {
        return yamcsInstance;
    }
}
