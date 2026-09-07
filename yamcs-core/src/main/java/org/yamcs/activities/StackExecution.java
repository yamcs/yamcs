package org.yamcs.activities;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.yamcs.ErrorInCommand;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.buckets.Bucket;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.security.User;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.YarchDatabase;

public class StackExecution extends ActivityExecution {

    private Processor processor;
    private Bucket bucket;
    private String stackName;
    private User user;

    private int seq = 0;
    private AtomicReference<PendingCommand> pendingCommandRef = new AtomicReference<>();

    private final Map<Parameter, ParameterValue> verifyValues = new ConcurrentHashMap<>();
    private AtomicReference<PendingVerify> pendingVerifyRef = new AtomicReference<>();

    public StackExecution(
            ActivityService activityService,
            StackExecutor executor,
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
        var mdb = MdbFactory.getInstance(yamcsInstance);
        var histManager = processor.getCommandHistoryManager();

        var bytes = bucket.getObjectAsync(stackName).get();
        if (bytes == null) {
            throw new IllegalArgumentException(
                    "No stack named '" + stackName + "' in bucket '" + bucket.getName() + "'");
        }
        var json = new String(bytes, StandardCharsets.UTF_8);
        var stack = Stack.fromJson(json, mdb);

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

        var prm = processor.getParameterRequestManager();
        var verifyParameters = stack.getSteps().stream()
                .filter(StackedVerify.class::isInstance)
                .map(StackedVerify.class::cast)
                .flatMap(v -> v.getCondition().stream())
                .map(comparison -> comparison.parameter())
                .collect(Collectors.toSet());

        Integer verifySubscriptionId = null;
        if (!verifyParameters.isEmpty()) {
            verifySubscriptionId = prm.addRequest(verifyParameters, (ParameterConsumer) (subId, items) -> {
                for (var pv : items) {
                    verifyValues.put(pv.getParameter(), pv);
                }
                var pending = pendingVerifyRef.get();
                if (pending != null && testCondition(pending.stackedVerify(), verifyValues)) {
                    pending.future().complete(true);
                }
            });
            // Seed with whatever is already known. Subscribing first means any update delivered
            // concurrently with this read already landed in verifyValues via the callback above;
            // putIfAbsent here only fills gaps, never clobbers a value the callback already set.
            for (var pv : prm.getValuesFromCache(verifyParameters)) {
                verifyValues.putIfAbsent(pv.getParameter(), pv);
            }
        }

        try {
            for (var step : stack.getSteps()) {
                if (step instanceof StackedCommand stackedCommand) {
                    runCommand(stack, stackedCommand);
                } else if (step instanceof StackedVerify stackedVerify) {
                    runVerify(stackedVerify);
                }
            }
        } finally {
            histManager.unsubscribeCommandHistory(histSubscription.subscriptionId);
            if (verifySubscriptionId != null) {
                prm.removeRequest(verifySubscriptionId);
            }
        }

        return null;
    }

    private void runVerify(StackedVerify stackedVerify)
            throws InterruptedException, ExecutionException, TimeoutException {
        logActivityInfo("Verifying " + stackedVerify);

        long delayTime = stackedVerify.getDelay();
        if (delayTime > 0) {
            logActivityInfo("Delaying verification for " + delayTime + " ms");
            Thread.sleep(delayTime);
        }

        if (testCondition(stackedVerify, verifyValues)) {
            return;
        }

        var successFuture = new CompletableFuture<Boolean>();
        pendingVerifyRef.set(new PendingVerify(stackedVerify, successFuture));
        try {
            // May have become true between the check above and wiring up pendingVerifyRef.
            if (testCondition(stackedVerify, verifyValues)) {
                return;
            }
            if (stackedVerify.getTimeout() > 0) {
                successFuture.get(stackedVerify.getTimeout(), TimeUnit.MILLISECONDS);
            } else {
                successFuture.get();
            }
        } catch (TimeoutException e) {
            logActivityError("Timeout while verifying");
            throw e;
        } finally {
            pendingVerifyRef.set(null);
        }
    }

    private boolean testCondition(StackedVerify stackedVerify, Map<Parameter, ParameterValue> values) {
        for (var comparison : stackedVerify.getCondition()) {
            var pval = values.get(comparison.parameter());
            if (pval == null || pval.getEngValue() == null) {
                return false;
            }

            var stringValue = pval.getEngValue().toString();
            var comparand = "" + comparison.value();

            switch (comparison.operator()) {
            case "eq":
                if (!stringValue.equals(comparand)) {
                    return false;
                }
                break;
            case "neq":
                if (stringValue.equals(comparand)) {
                    return false;
                }
                break;
            case "lt":
                if (!isNumeric(stringValue) || !isNumeric(comparand)) {
                    return false;
                }
                if (Double.parseDouble(stringValue) >= Double.parseDouble(comparand)) {
                    return false;
                }
                break;
            case "lte":
                if (!isNumeric(stringValue) || !isNumeric(comparand)) {
                    return false;
                }
                if (Double.parseDouble(stringValue) > Double.parseDouble(comparand)) {
                    return false;
                }
                break;
            case "gt":
                if (!isNumeric(stringValue) || !isNumeric(comparand)) {
                    return false;
                }
                if (Double.parseDouble(stringValue) <= Double.parseDouble(comparand)) {
                    return false;
                }
                break;
            case "gte":
                if (!isNumeric(stringValue) || !isNumeric(comparand)) {
                    return false;
                }
                if (Double.parseDouble(stringValue) < Double.parseDouble(comparand)) {
                    return false;
                }
                break;
            }

        }

        return true;
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void runCommand(Stack stack, StackedCommand stackedCommand)
            throws UnknownHostException, ErrorInCommand, YamcsException, InterruptedException, ExecutionException {
        logActivityInfo("Running command " + stackedCommand);

        var yamcs = YamcsServer.getServer();
        var cmdManager = processor.getCommandingManager();
        var ydb = YarchDatabase.getInstance(yamcsInstance);
        var origin = InetAddress.getLocalHost().getHostName();

        var args = new LinkedHashMap<String, Object>();
        for (var arg : stackedCommand.getAssignments().entrySet()) {
            args.put(arg.getKey().getName(), arg.getValue());
        }

        var preparedCommand = cmdManager.buildCommand(
                stackedCommand.getMetaCommand(), args, origin, seq++, user);
        if (stackedCommand.getComment() != null) {
            preparedCommand.setComment(stackedCommand.getComment());
        }
        if (stackedCommand.getStream() != null) {
            var stream = ydb.getStream(stackedCommand.getStream());
            preparedCommand.setTcStream(stream);
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

        if (ackStatus != AckStatus.OK && ackStatus != AckStatus.DISABLED && ackStatus != AckStatus.NA) {
            var message = acknowledgment + " acknowledgment failed: " + ackStatus;
            logActivityError(message);
            throw new YamcsException(message);
        }

        int waitTime = stackedCommand.getWaitTime();
        if (waitTime == -1) {
            waitTime = stack.getWaitTime();
        }
        if (waitTime > 0) {
            logActivityInfo("Waiting for " + waitTime + " ms");
            Thread.sleep(waitTime);
        }
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
                    if (isTerminal(ackStatus)) {
                        acknowledgedFuture.complete(ackStatus);
                    }
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
                    if (isTerminal(ackStatus)) {
                        acknowledgedFuture.complete(ackStatus);
                    }
                }
            }
        }

        private static boolean isTerminal(AckStatus ackStatus) {
            return switch (ackStatus) {
            case OK, NOK, TIMEOUT, CANCELLED, DISABLED, NA -> true;
            case SCHEDULED, PENDING -> false;
            };
        }
    }

    private record PendingVerify(StackedVerify stackedVerify, CompletableFuture<Boolean> future) {
    }
}
