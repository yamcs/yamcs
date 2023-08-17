package org.yamcs.cascading;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.client.Command;
import org.yamcs.client.CommandListener;
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.client.processor.ProcessorClient.CommandBuilder;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Mdb.ArgumentAssignmentInfo;
import org.yamcs.protobuf.Mdb.ArgumentInfo;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

public class YamcsTcLink extends AbstractTcDataLink {
    private final ArrayList<CommandMapData> commandMapDataList = new ArrayList<>();
    YamcsLink parentLink;
    Map<String, PreparedCommand> sentCommands = new ConcurrentHashMap<>();
    Map<String, CommandInfo> upstreamCmdCache = new ConcurrentHashMap<>();
    private CommandSubscription cmdSubscription;
    private ProcessorClient procClient;
    private MissionDatabaseClient mdbClient;
    private String cmdOrigin;
    private Set<String> keepUpstreamAcks = new HashSet<>();

    public YamcsTcLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        this.cmdOrigin = YamcsServer.getServer().getServerId() + "-" + instance + "-" + this.linkName;
        List<String> l;
        if (config.containsKey("keepUpstreamAcks")) {
            l = config.getList("keepUpstreamAcks");
        } else {
            l = List.of(CommandHistoryPublisher.CcsdsSeq_KEY);
        }
        if (config.containsKey("commandMapping")) {
            List<YConfiguration> commandMapConfigList = config.getConfigList("commandMapping");
            commandMapConfigList.forEach(conf -> commandMapDataList.add(new CommandMapData(conf)));
        } else {
            // add default direct command mapping.
            log.warn("default config");
            commandMapDataList.add(new CommandMapData());
        }

        keepUpstreamAcks = new HashSet<>(l);
    }

    public boolean sendCommand(PreparedCommand pc) {
        for (CommandMapData data : commandMapDataList) {
            if (data.getCommandType().equals(CommandMapData.CommandType.DEFAULT)) {
                return sendDirectCommand(pc, data);
            }
            String pcCommandPath;
            if (data.getLocalPath().endsWith("/")) {
                pcCommandPath = pc.getMetaCommand().getQualifiedName()
                        .substring(0, pc.getMetaCommand().getQualifiedName().lastIndexOf("/") + 1);
            } else {
                pcCommandPath = pc.getMetaCommand().getQualifiedName();
            }

            if (pcCommandPath.startsWith(data.getLocalPath())) {
                switch (data.getCommandType()) {
                case DIRECT:
                    return sendDirectCommand(pc, data);
                case EMBEDDED_BINARY:
                    return sendEmbeddedBinaryCommand(pc, data);
                }
            }
        }
        return true;
    }

    private boolean sendEmbeddedBinaryCommand(PreparedCommand pc, CommandMapData data) {
        CommandInfo upstreamCmd = getUpstreamCmd(data.getUpstreamPath());
        if (upstreamCmd == null) {
            String msg = "Cannot send the command because upstream command definition is not available";
            failedCommand(pc.getCommandId(), msg);
            log.warn(msg);
            return true;
        }
        CommandBuilder cb = procClient.prepareCommand(data.getUpstreamPath());
        cb.withOrigin(cmdOrigin);
        long count = dataCount.getAndIncrement();
        cb.withSequenceNumber((int) count);

        sentCommands.put(cmdOrigin + "-" + count, pc);

        if (pc.getComment() != null) {
            cb.withComment(pc.getComment());
        }
        List<ArgumentInfo> Args = getRequiredArguments(upstreamCmd);
        boolean foundArgument = false;
        for (ArgumentInfo entry : Args) {
            String argName = entry.getName();
            if (entry.getName().equals(data.getUpstreamArgumentName())) {
                // TODO aggregates/arrays*/
                foundArgument = true;
                cb.withArgument(data.getUpstreamArgumentName(), pc.getBinary());
            } else {
                log.warn("More required arguments than the binary argument: {}", entry.getName());
            }
        }
        if (!foundArgument) {
            String msg = "Cannot send the command because upstream argument is not found";
            failedCommand(pc.getCommandId(), msg);
            log.warn(msg);
            return true;
        }

        // we take the time now because after the command is issued, the current time will be after the upstream
        // Queued/Released timestamps
        long time = getCurrentTime();
        cb.issue().whenComplete((c, t) -> {
            if (t != null) {
                log.warn("Error sending command ", t);
                failedCommand(pc.getCommandId(), t.getMessage());
            } else {
                commandHistoryPublisher.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, time, AckStatus.OK);
            }
        });
        return true;
    }

    private boolean sendDirectCommand(PreparedCommand pc, CommandMapData data) {
        String upstreamCmdName;
        // keep supporting the default case where all downstream commands are mapped onto upstream commands with the same name.
        if (data.getCommandType() == CommandMapData.CommandType.DEFAULT) {
            upstreamCmdName = pc.getMetaCommand().getQualifiedName();
        } else {
            String upstreamCmdPath = data.getUpstreamPath();
            log.warn(upstreamCmdPath);
            if (upstreamCmdPath.endsWith("/")) {
                String pcCommandName = pc.getCmdName();
                upstreamCmdName = upstreamCmdPath + pcCommandName.substring(data.getLocalPath().length());
                log.warn(upstreamCmdName);
            } else {
                upstreamCmdName = upstreamCmdPath;
            }
        }

        CommandInfo upstreamCmd = getUpstreamCmd(upstreamCmdName);

        if (upstreamCmd == null) {
            String msg = "Cannot send the command because upstream command definition is not available";
            failedCommand(pc.getCommandId(), msg);
            log.warn(msg);
            return true;
        }

        CommandBuilder cb = procClient.prepareCommand(pc.getCmdName());
        cb.withOrigin(cmdOrigin);
        long count = dataCount.getAndIncrement();
        cb.withSequenceNumber((int) count);

        sentCommands.put(cmdOrigin + "-" + count, pc);

        if (pc.getComment() != null) {
            cb.withComment(pc.getComment());
        }
        List<ArgumentInfo> reqArgs = getRequiredArguments(upstreamCmd);

        for (Entry<Argument, ArgumentValue> entry : pc.getArgAssignment().entrySet()) {
            String argName = entry.getKey().getName();

            if (reqArgs.stream().anyMatch(ai -> argName.equals(ai.getName()))) {
                // TODO aggregates/arrays
                cb.withArgument(argName, toClientValue(entry.getValue()));
            }
        }

        // we take the time now because after the command is issued, the current time will be after the upstream
        // Queued/Released timestamps
        long time = getCurrentTime();
        cb.issue().whenComplete((c, t) -> {
            if (t != null) {
                log.warn("Error sending command ", t);
                failedCommand(pc.getCommandId(), t.getMessage());
            } else {
                commandHistoryPublisher.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, time, AckStatus.OK);
            }
        });

        return true;
    }

    private Object toClientValue(ArgumentValue value) {
        return ValueUtility.getYarchValue(value.getEngValue());
    }

    // extract from the list of passed on arguments the ones required by the upstream
    private List<ArgumentInfo> getRequiredArguments(CommandInfo upstreamCmd) {
        Set<String> assignedArgs = new HashSet<>();
        CommandInfo ci = upstreamCmd;
        while (true) {
            for (ArgumentAssignmentInfo aai : ci.getArgumentAssignmentList()) {
                assignedArgs.add(aai.getName());
            }
            if (ci.hasBaseCommand()) {
                ci = ci.getBaseCommand();
            } else {
                break;
            }
        }

        ci = upstreamCmd;
        List<ArgumentInfo> reqArgs = new ArrayList<>();
        while (true) {
            for (ArgumentInfo ai : ci.getArgumentList()) {
                if (!assignedArgs.contains(ai.getName())) {
                    reqArgs.add(ai);
                }
            }
            if (ci.hasBaseCommand()) {
                ci = ci.getBaseCommand();
            } else {
                break;
            }
        }
        return reqArgs;
    }

    // extract from the list of passed on arguments the ones required by the upstream
    private List<ArgumentAssignmentInfo> getArguments(CommandInfo upstreamCmd) {
        List<ArgumentAssignmentInfo> assignedArgs = new ArrayList<>();
        CommandInfo ci = upstreamCmd;
        while (true) {
            for (ArgumentAssignmentInfo aai : ci.getArgumentAssignmentList()) {
                assignedArgs.add(aai);
            }
            if (ci.hasBaseCommand()) {
                ci = ci.getBaseCommand();
            } else {
                break;
            }
        }

        return assignedArgs;
    }

    private CommandInfo getUpstreamCmd(String upstreamCmdName) {
        CommandInfo cinfo = upstreamCmdCache.get(upstreamCmdName);
        if (cinfo == null) {
            try {
                log.debug("Retrieving information about command {} from upstream", upstreamCmdName);
                cinfo = mdbClient.getCommand(upstreamCmdName).get();
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to retrieve command definition " + upstreamCmdName + " from upstream: " + e);
                return null;
            }
            upstreamCmdCache.put(upstreamCmdName, cinfo);
        }
        return cinfo;
    }

    @Override
    protected void doStart() {
        if (!isEffectivelyDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doDisable() {
        if (cmdSubscription != null) {
            cmdSubscription.cancel(true);
            cmdSubscription = null;
        }
    }

    /**
     * Called when a command history update is received from the upstream server
     *
     * @param command
     * @param cmdHistEntry
     */
    void commandUpdated(Command command, CommandHistoryEntry che) {
        PreparedCommand pc = sentCommands.get(command.getOrigin() + "-" + command.getSequenceNumber());
        if (pc != null) {
            for (CommandHistoryAttribute cha : che.getAttrList()) {
                String name = transformCommandHistoryAttributeName(cha.getName());
                publishCmdHistory(pc.getCommandId(), name, cha.getValue());
            }

        } // else TODO: should we add to command history commands not sent by us?
    }

    private String transformCommandHistoryAttributeName(String name) {
        if (keepUpstreamAcks.contains(name)) {
            return name;
        } else {
            return "yamcs<" + parentLink.getUpstreamName() + ">_" + name;
        }
    }

    private void publishCmdHistory(CommandId cmdId, String name, Value value) {
        switch (value.getType()) {
        case SINT32:
            commandHistoryPublisher.publish(cmdId, name, value.getSint32Value());
            break;
        case UINT32:
            commandHistoryPublisher.publish(cmdId, name, value.getUint32Value());
            break;
        case UINT64:
            commandHistoryPublisher.publish(cmdId, name, value.getUint64Value());
            break;
        case SINT64:
            commandHistoryPublisher.publish(cmdId, name, value.getSint64Value());
            break;
        case STRING:
            commandHistoryPublisher.publish(cmdId, name, value.getStringValue());
            break;
        case TIMESTAMP:
            commandHistoryPublisher.publish(cmdId, name, value.getTimestampValue());
            break;
        case BINARY:
            commandHistoryPublisher.publish(cmdId, name, value.getBinaryValue().toByteArray());
            break;
        default:
            log.warn("Cannot publish command history attributes of type {}", value.getType());
        }
    }

    @Override
    public void doEnable() {
        if (cmdSubscription != null && !cmdSubscription.isDone()) {
            return;
        }
        WebSocketClient wsclient = parentLink.getClient().getWebSocketClient();
        if (wsclient.isConnected()) {
            subscribeCommanding();
        }
    }

    private void subscribeCommanding() {
        YamcsClient yclient = parentLink.getClient();

        procClient = yclient.createProcessorClient(parentLink.getUpstreamInstance(), parentLink.getUpstreamProcessor());
        mdbClient = yclient.createMissionDatabaseClient(parentLink.getUpstreamInstance());
        cmdSubscription = yclient.createCommandSubscription();
        cmdSubscription.addListener(new CommandListener() {

            @Override
            public void onUpdate(Command command, CommandHistoryEntry cmdHistEntry) {
                commandUpdated(command, cmdHistEntry);
            }

            public void onError(Throwable t) {
                eventProducer.sendWarning("Got error when subscribign to commanding: " + t);
            }

            @Override
            public void onUpdate(Command command) {
            }

        });
        cmdSubscription.sendMessage(SubscribeCommandsRequest.newBuilder().setInstance(parentLink.getUpstreamInstance())
                .setProcessor(parentLink.getUpstreamProcessor()).build());
    }

    @Override
    protected Status connectionStatus() {
        Status parentStatus = parentLink.connectionStatus();
        if (parentStatus == Status.OK) {
            boolean ok = cmdSubscription != null && !cmdSubscription.isDone();
            return ok ? Status.OK : Status.UNAVAIL;
        } else {
            return parentStatus;
        }
    }

    @Override
    protected void doStop() {
        if (!isDisabled()) {
            doDisable();
        }
        notifyStopped();
    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }
}
