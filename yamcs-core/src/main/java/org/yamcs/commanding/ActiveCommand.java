package org.yamcs.commanding;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.Processor;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

/**
 * A command which is just being sent (maybe in the queue) or that has been sent and command verifiers are pending.
 *
 */
public class ActiveCommand implements CommandHistoryConsumer {
    final PreparedCommand preparedCommand;
    final Processor processor;
    final static Log log = new Log(ActiveCommand.class);
    // Initialised with the command attributes and updated with the command history events
    LastValueCache cmdParamCache = new LastValueCache();

    // used when a command has a transmissionConstraint with timeout
    // when the command is ready to go, but is waiting for a transmission constraint, this is set to true
    private boolean pendingTransmissionConstraint;

    // this is the time when the clock starts ticking for fullfilling the transmission constraint
    // -1 means it has not been set yet
    private long transmissionConstraintCheckStart = -1;

    CopyOnWriteArrayList<ParameterProcessor> cmdParamProcessors = new CopyOnWriteArrayList<>();

    public ActiveCommand(Processor processor, PreparedCommand preparedCommand) {
        this.preparedCommand = preparedCommand;
        this.processor = processor;
        initCmdParams();
    }

    void initCmdParams() {
        Mdb mdb = processor.getMdb();
        for (CommandHistoryAttribute cha : preparedCommand.getAttributes()) {
            String fqn = Mdb.YAMCS_CMD_SPACESYSTEM_NAME + "/" + cha.getName();
            if (mdb.getParameter(fqn) == null) {
                // if it was required in the algorithm, it would be already in the system parameter db
                continue;
            }
            Parameter p = mdb.getParameter(fqn);
            ParameterValue pv = new ParameterValue(p);
            pv.setEngValue(ValueUtility.fromGpb(cha.getValue()));
            cmdParamCache.add(pv);
        }
    }

    public CommandId getCommandId() {
        return preparedCommand.getCommandId();
    }

    public boolean isPendingTransmissionConstraints() {
        return pendingTransmissionConstraint;
    }

    public void setPendingTransmissionConstraints(boolean b) {
        this.pendingTransmissionConstraint = b;
    }

    public long getTransmissionConstraintCheckStart() {
        return transmissionConstraintCheckStart;
    }

    public void setTransmissionConstraintCheckStart(long transmissionConstraintCheckStart) {
        this.transmissionConstraintCheckStart = transmissionConstraintCheckStart;
    }

    public MetaCommand getMetaCommand() {
        return preparedCommand.getMetaCommand();
    }

    public Map<Argument, ArgumentValue> getArguments() {
        return preparedCommand.getArgAssignment();
    }

    public LastValueCache getCmdParamCache() {
        return cmdParamCache;
    }

    public PreparedCommand getPreparedCommand() {
        return preparedCommand;
    }

    public String getCmdName() {
        return preparedCommand.getCmdName();
    }

    /**
     * 
     * @return true if the transmission constraints have to be disabled for this command
     */
    public boolean disableTransmissionConstraints() {
        return preparedCommand.disableTransmissionConstraints();
    }

    /**
     * 
     * @return true if the command verifiers have to be disabled for this command
     */
    public boolean disableCommandVerifiers() {
        return preparedCommand.disableCommandVerifiers();
    }

    public Map<String, VerifierConfig> getVerifierOverride() {
        return preparedCommand.getVerifierOverride();
    }

    void subscribeCmdParams(ParameterProcessor processor) {
        cmdParamProcessors.add(processor);
    }

    void unsubscribeCmdParams(ParameterProcessor processor) {
        cmdParamProcessors.remove(processor);
    }

    // called from the command history when things are added in the stream
    @Override
    public void updatedCommand(CommandId cmdId, long time, List<Attribute> attrs) {
        if (!cmdId.equals(getCommandId())) {// sanity check
            log.error("Got a command history update for a different command: {}", cmdId);
            return;
        }
        Mdb mdb = processor.getMdb();
        ProcessingData data = ProcessingData.createForCmdProcessing(processor.getLastValueCache(), getArguments(),
                cmdParamCache);

        ParameterValueList cmdParams = data.getCmdParams();

        for (Attribute attr : attrs) {
            String fqn = Mdb.YAMCS_CMDHIST_SPACESYSTEM_NAME + "/" + attr.getKey();
            Parameter p = mdb.getParameter(fqn);

            if (p == null) {
                // if it was required in the algorithm, it would be in the MDB
                log.trace("Not adding {} to the context parameter list because it is not defined in the MDB", fqn);
            } else {
                ParameterValue pv = new ParameterValue(p);
                pv.setEngValue(attr.getValue());
                cmdParams.add(pv);
            }
        }

        for (ParameterProcessor proc : cmdParamProcessors) {
            proc.process(data);
        }

        cmdParamCache.addAll(cmdParams);
        ParameterValueList tmParams = data.getTmParams();
        if (!tmParams.isEmpty()) {
            ProcessingData tmData = ProcessingData.cloneForTm(data);
            processor.getParameterProcessorManager().process(tmData);
        }
    }

    /**
     * One line string id useful for logging
     * 
     * @return
     */
    public String getLoggingId() {
        return preparedCommand.getLoggingId();
    }

    @Override
    public void addedCommand(PreparedCommand pc) {
        // this will never be called since we are subscribed to this command only
    }
}
