package org.yamcs.activities;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.security.User;
import org.yamcs.yarch.Bucket;

public class CommandStackExecution extends ActivityExecution {

    private Processor processor;
    private Bucket bucket;
    private String stackName;
    private User user;

    public CommandStackExecution(
            ActivityService activityService,
            CommandStackExecutor executor,
            Activity activity,
            Processor processor,
            Bucket bucket,
            String stackName,
            User user) {
        super(activityService, executor, activity);
        this.processor = processor;
        this.bucket = bucket;
        this.stackName = stackName;
        this.user = user;
    }

    @Override
    public Void run() throws Exception {
        var yamcs = YamcsServer.getServer();
        var cmdManager = processor.getCommandingManager();
        var histManager = processor.getCommandHistoryManager();
        var mdb = MdbFactory.getInstance(processor.getInstance());
        var origin = InetAddress.getLocalHost().getHostName();
        var seq = 0;

        var bytes = bucket.getObject(stackName);
        var json = new String(bytes, StandardCharsets.UTF_8);
        var stack = CommandStack.fromJson(json, mdb);

        var pendingCommandRef = new AtomicReference<PendingCommand>();

        var histSubscription = histManager.subscribeCommandHistory(
                null, processor.getCurrentTime(), new CommandHistoryConsumer() {
                    @Override
                    public void addedCommand(PreparedCommand pc) {
                        var currentCommand = pendingCommandRef.get();
                        if (currentCommand != null && currentCommand.cmdId.equals(pc.getCommandId())) {
                            currentCommand.checkIfAcknowledged0(pc.getAttributes());
                        }
                    }

                    @Override
                    public void updatedCommand(CommandId cmdId, long time, List<Attribute> attrs) {
                        var currentCommand = pendingCommandRef.get();
                        if (currentCommand != null && currentCommand.cmdId.equals(cmdId)) {
                            currentCommand.checkIfAcknowledged(attrs);
                        }
                    }
                });

        try {
            for (var stackedCommand : stack.getCommands()) {
                logActivityInfo("Running command " + stackedCommand);

                var args = new LinkedHashMap<String, Object>();
                for (var arg : stackedCommand.getAssignments().entrySet()) {
                    args.put(arg.getKey().getName(), arg.getValue());
                }

                var preparedCommand = cmdManager.buildCommand(
                        stackedCommand.getMetaCommand(), args, origin, seq++, user);
                if (stackedCommand.getComment() != null) {
                    preparedCommand.setComment(stackedCommand.getComment());
                }

                for (var entry : stackedCommand.getExtra().entrySet()) {
                    var commandOption = yamcs.getCommandOption(entry.getKey());
                    if (commandOption == null) {
                        throw new IllegalArgumentException("Unknown command option '" + entry.getKey() + "'");
                    }
                    preparedCommand.addAttribute(CommandHistoryAttribute.newBuilder()
                            .setName(entry.getKey())
                            .setValue(commandOption.coerceValue(entry.getValue()))
                            .build());
                }

                var acknowledgment = stackedCommand.getAcknowledgment();
                if (acknowledgment == null) {
                    acknowledgment = stack.getAcknowledgment();
                }
                var pendingCommand = new PendingCommand(preparedCommand.getCommandId(), acknowledgment);
                pendingCommandRef.set(pendingCommand);

                cmdManager.sendCommand(user, preparedCommand);

                logActivityInfo("Waiting for " + acknowledgment + " acknowledgment");
                // No timeout, this should come from the verifier itself
                var ackStatus = pendingCommand.acknowledgedFuture.get();
                logActivityInfo(acknowledgment + ": " + ackStatus);

                int waitTime = stackedCommand.getWaitTime();
                if (waitTime == -1) {
                    waitTime = stack.getWaitTime();
                }
                if (waitTime > 0) {
                    logActivityInfo("Waiting for " + waitTime + " ms");
                    Thread.sleep(waitTime);
                }
            }
        } finally {
            histManager.unsubscribeCommandHistory(histSubscription.subscriptionId);
        }

        return null;
    }

    @Override
    public void stop() throws Exception {
        // NOP
    }

    private static class PendingCommand {
        final CommandId cmdId;
        final String acknowledgment;
        final CompletableFuture<AckStatus> acknowledgedFuture = new CompletableFuture<>();

        PendingCommand(CommandId cmdId, String acknowledgment) {
            this.cmdId = cmdId;
            this.acknowledgment = acknowledgment;
        }

        void checkIfAcknowledged0(List<CommandHistoryAttribute> attrs) {
            if (acknowledgedFuture.isDone()) {
                return;
            }
            var ackStatusKey = acknowledgment + CommandHistoryPublisher.SUFFIX_STATUS;
            for (var attr : attrs) {
                if (attr.getName().equals(ackStatusKey)) {
                    var ackStatus = AckStatus.valueOf(attr.getValue().getStringValue());
                    acknowledgedFuture.complete(ackStatus);
                }
            }
        }

        void checkIfAcknowledged(List<Attribute> attrs) {
            if (acknowledgedFuture.isDone()) {
                return;
            }
            var ackStatusKey = acknowledgment + CommandHistoryPublisher.SUFFIX_STATUS;
            for (var attr : attrs) {
                if (attr.getKey().equals(ackStatusKey)) {
                    var ackStatus = AckStatus.valueOf(attr.getValue().getStringValue());
                    acknowledgedFuture.complete(ackStatus);
                }
            }
        }
    }
}
