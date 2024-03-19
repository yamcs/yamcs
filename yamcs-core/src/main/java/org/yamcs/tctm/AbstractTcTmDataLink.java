package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.YObjectLoader;

/**
 * Base class for TM/TC links.
 */
public abstract class AbstractTcTmDataLink extends AbstractTmDataLink implements TcDataLink {
    protected CommandHistoryPublisher commandHistoryPublisher;
    protected AtomicLong dataOutCount = new AtomicLong();
    protected CommandPostprocessor cmdPostProcessor;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;
    PacketInputStream packetInputStream;
    OutputStream outputStream;

    @Override
    public Spec getDefaultSpec() {
        var spec = super.getDefaultSpec();        
        spec.addOption("commandPostprocessorClassName", OptionType.STRING);
        spec.addOption("commandPostprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);

        // Setup tc postprocessor
        initPostprocessor(yamcsInstance, config);
    }

    protected void initPostprocessor(String instance, YConfiguration config) throws ConfigurationException {
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
    
    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
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
    public long getDataOutCount() {
        return dataOutCount.get();
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        dataOutCount.set(0);
    }
}
