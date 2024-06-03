package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.TmPacket;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.utils.YObjectLoader;

/**
 * Base class for TM/TC/parameter links.
 */
public abstract class AbstractTcTmParamLink extends AbstractLink
        implements TmPacketDataLink, TcDataLink, ParameterDataLink {

    // TC related fields
    protected CommandPostprocessor cmdPostProcessor;
    protected CommandHistoryPublisher commandHistoryPublisher;

    // TM packet related fields
    protected AtomicLong packetCount = new AtomicLong(0);
    private TmSink tmSink;
    protected boolean updateSimulationTime;
    String packetPreprocessorClassName;
    YConfiguration packetPreprocessorArgs;
    protected PacketPreprocessor packetPreprocessor;
    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";

    // Parameter related fields
    protected ParameterSink parameterSink;
    protected AtomicLong parameterCount = new AtomicLong(0);

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;
    PacketInputStream packetInputStream;
    OutputStream outputStream;

    @Override
    public Spec getDefaultSpec() {
        var spec = super.getDefaultSpec();        
        spec.addOption("commandPostprocessorClassName", OptionType.STRING);
        spec.addOption("commandPostprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);

        spec.addOption("packetPreprocessorClassName", OptionType.STRING);
        spec.addOption("packetPreprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("updateSimulationTime", OptionType.BOOLEAN).withDefault(false);

        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);

        initTc(yamcsInstance, config);
        initTm(yamcsInstance, config);

    }

    protected void initTc(String instance, YConfiguration config) throws ConfigurationException {
        String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
        YConfiguration commandPostprocessorArgs = null;

        // The GenericCommandPostprocessor class does nothing if there are no arguments, which is what we want.
        if (config != null) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName",
                    GenericCommandPostprocessor.class.getName());
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

        // Instantiate
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
        }
    }

    protected void initTm(String instance, YConfiguration config) {
        if (config.containsKey(CFG_PREPRO_CLASS)) {
            this.packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS);
        } else {
            this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
        }
        if (config.containsKey("packetPreprocessorArgs")) {
            this.packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
        }

        try {
            if (packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance,
                        packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        }

        updateSimulationTime = config.getBoolean("updateSimulationTime", false);
        if (updateSimulationTime) {
            if (timeService instanceof SimulationTimeService) {
                SimulationTimeService sts = (SimulationTimeService) timeService;
                sts.setTime0(0);
            } else {
                throw new ConfigurationException(
                        "updateSimulationTime can only be used together with SimulationTimeService "
                                + "(add 'timeService: org.yamcs.time.SimulationTimeService' in yamcs.<instance>.yaml)");
            }
        }

    }
    /**
     * Postprocesses the command, unless postprocessing is disabled.
     * 
     * @return potentially modified binary, or {@code null} to indicate that the command should not be handled further.
     */
    protected byte[] postprocess(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (!pc.disablePostprocessing()) {
            binary = cmdPostProcessor.process(pc);
            if (binary == null) {
                log.warn("command postprocessor did not process the command");
            }
        }
        return binary;
    }
    
    /**
     * Sends the packet downstream for processing.
     * <p>
     * Starting in Yamcs 5.2, if the updateSimulationTime option is set on the link configuration,
     * <ul>
     * <li>the timeService is expected to be SimulationTimeService</li>
     * <li>at initialization, the time0 is set to 0</li>
     * <li>upon each packet received, the generationTime (as set by the pre-processor) is used to update the simulation
     * elapsed time</li>
     * </ul>
     * <p>
     * Should be called by all sub-classes (instead of directly calling {@link TmSink#processPacket(TmPacket)}
     * 
     * @param tmpkt
     */
    protected void processPacket(TmPacket tmpkt) {
        tmSink.processPacket(tmpkt);
        if (updateSimulationTime) {
            SimulationTimeService sts = (SimulationTimeService) timeService;
            if (!tmpkt.isInvalid()) {
                sts.setSimElapsedTime(tmpkt.getGenerationTime());
            }
        }
    }

    /** Send to command history the failed command */
    protected void failedCommand(CommandId commandId, String reason) {
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = getCurrentTime();
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY, currentTime, AckStatus.NOK, reason);
        commandHistoryPublisher.commandFailed(commandId, currentTime, reason);
    }

    /**
     * send an ack in the command history that the command has been sent out of the link
     * 
     * @param commandId
     */
    protected void ackCommand(CommandId commandId) {
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY, getCurrentTime(), AckStatus.OK);
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
    }

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        this.parameterSink = parameterSink;
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }
}
