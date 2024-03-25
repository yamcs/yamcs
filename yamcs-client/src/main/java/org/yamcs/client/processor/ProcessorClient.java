package org.yamcs.client.processor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.Command;
import org.yamcs.client.Helpers;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.processor.ProcessorClient.GetOptions.FromCacheOption;
import org.yamcs.client.processor.ProcessorClient.GetOptions.GetOption;
import org.yamcs.client.processor.ProcessorClient.GetOptions.TimeoutOption;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.protobuf.AcceptCommandRequest;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.BlockQueueRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.CommandsApiClient;
import org.yamcs.protobuf.DisableQueueRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.EnableQueueRequest;
import org.yamcs.protobuf.GetParameterValueRequest;
import org.yamcs.protobuf.GetProcessorRequest;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.MdbOverrideApiClient;
import org.yamcs.protobuf.ProcessingApiClient;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.QueuesApiClient;
import org.yamcs.protobuf.RejectCommandRequest;
import org.yamcs.protobuf.SetParameterValueRequest;
import org.yamcs.protobuf.UpdateAlgorithmRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.UpdateParameterRequest;
import org.yamcs.protobuf.UpdateParameterRequest.ActionType;
import org.yamcs.protobuf.Yamcs.Value;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;

public class ProcessorClient {

    private String instance;
    private String processor;

    private ProcessingApiClient processingService;
    private CommandsApiClient commandService;
    private MdbOverrideApiClient mdbOverrideService;
    private QueuesApiClient queueService;

    public ProcessorClient(MethodHandler methodHandler, String instance, String processor) {
        this.instance = instance;
        this.processor = processor;
        processingService = new ProcessingApiClient(methodHandler);
        commandService = new CommandsApiClient(methodHandler);
        mdbOverrideService = new MdbOverrideApiClient(methodHandler);
        queueService = new QueuesApiClient(methodHandler);
    }

    public String getInstance() {
        return instance;
    }

    public String getProcessor() {
        return processor;
    }

    public CompletableFuture<ProcessorInfo> getInfo() {
        GetProcessorRequest.Builder requestb = GetProcessorRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor);
        CompletableFuture<ProcessorInfo> f = new CompletableFuture<>();
        processingService.getProcessor(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterValue> getValue(String parameter, GetOption... options) {
        GetParameterValueRequest.Builder requestb = GetParameterValueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter);
        for (GetOption option : options) {
            if (option instanceof FromCacheOption) {
                requestb.setFromCache(((FromCacheOption) option).fromCache);
            } else if (option instanceof TimeoutOption) {
                requestb.setTimeout(((TimeoutOption) option).timeout);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<ParameterValue> f = new CompletableFuture<>();
        processingService.getParameterValue(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<ParameterValue>> getValues(List<String> parameters,
            GetOption... options) {
        BatchGetParameterValuesRequest.Builder requestb = BatchGetParameterValuesRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor);
        for (GetOption option : options) {
            if (option instanceof FromCacheOption) {
                requestb.setFromCache(((FromCacheOption) option).fromCache);
            } else if (option instanceof TimeoutOption) {
                requestb.setTimeout(((TimeoutOption) option).timeout);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        for (String parameter : parameters) {
            requestb.addId(Helpers.toNamedObjectId(parameter));
        }
        CompletableFuture<BatchGetParameterValuesResponse> f = new CompletableFuture<>();
        processingService.batchGetParameterValues(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getValueList());
    }

    public CompletableFuture<Void> setValue(String parameter, Value value) {
        SetParameterValueRequest request = SetParameterValueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setValue(value)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.setParameterValue(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> setValues(Map<String, Value> values) {
        BatchSetParameterValuesRequest.Builder requestb = BatchSetParameterValuesRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor);
        for (Entry<String, Value> entry : values.entrySet()) {
            requestb.addRequest(BatchSetParameterValuesRequest.SetParameterValueRequest.newBuilder()
                    .setId(Helpers.toNamedObjectId(entry.getKey()))
                    .setValue(entry.getValue()).build());
        }
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.batchSetParameterValues(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Command> issueCommand(String command, Map<String, ?> arguments) {
        IssueCommandRequest.Builder requestb = IssueCommandRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(command)
                .setArgs(WellKnownTypes.toStruct(arguments));
        CompletableFuture<IssueCommandResponse> f = new CompletableFuture<>();
        commandService.issueCommand(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(Command::new);
    }

    public CompletableFuture<Command> issueCommand(String command, Map<String, ?> arguments, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return issueCommand(command, arguments).get();
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }, executor);
    }

    public CommandBuilder prepareCommand(String command) {
        return new CommandBuilder(commandService, instance, processor, command);
    }

    public CompletableFuture<Void> pause() {
        EditProcessorRequest request = EditProcessorRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setState("paused")
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.editProcessor(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> resume() {
        EditProcessorRequest request = EditProcessorRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setState("running")
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.editProcessor(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> seek(Instant instant) {
        EditProcessorRequest request = EditProcessorRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setSeek(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()))
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.editProcessor(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> changeSpeed(String speed) {
        EditProcessorRequest request = EditProcessorRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setSpeed(speed)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.editProcessor(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> updateCommand(String command, String commandId, String attribute, Value value) {
        return updateCommand(command, commandId, Collections.singletonMap(attribute, value));
    }

    public CompletableFuture<Void> updateCommand(String command, String commandId, Map<String, Value> attributes) {
        UpdateCommandHistoryRequest.Builder request = UpdateCommandHistoryRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(command) // TODO update API to not require command name. Id is sufficient.
                .setId(commandId);
        for (Entry<String, Value> entry : attributes.entrySet()) {
            request.addAttributes(CommandHistoryAttribute.newBuilder()
                    .setName(entry.getKey())
                    .setValue(entry.getValue()));
        }

        CompletableFuture<Empty> f = new CompletableFuture<>();
        commandService.updateCommandHistory(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<ParameterTypeInfo> setDefaultCalibrator(String parameter, CalibratorInfo calibrator) {
        UpdateParameterRequest request = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.SET_DEFAULT_CALIBRATOR)
                .setDefaultCalibrator(calibrator)
                .build();
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterTypeInfo> setCalibrators(String parameter, CalibratorInfo defaultCalibrator,
            List<ContextCalibratorInfo> contextCalibrators) {
        UpdateParameterRequest.Builder requestb = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.SET_CALIBRATORS)
                .addAllContextCalibrator(contextCalibrators);
        if (defaultCalibrator != null) {
            requestb.setDefaultCalibrator(defaultCalibrator);
        }
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterTypeInfo> removeCalibrators(String parameter) {
        return setCalibrators(parameter, null, Collections.emptyList());
    }

    public CompletableFuture<ParameterTypeInfo> revertCalibrators(String parameter) {
        UpdateParameterRequest request = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.RESET_CALIBRATORS)
                .build();
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterTypeInfo> setDefaultAlarm(String parameter, AlarmInfo alarm) {
        UpdateParameterRequest request = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.SET_DEFAULT_ALARMS)
                .setDefaultAlarm(alarm)
                .build();
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterTypeInfo> setAlarms(String parameter, AlarmInfo defaultAlarm,
            List<ContextAlarmInfo> contextAlarms) {
        UpdateParameterRequest.Builder requestb = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.SET_ALARMS)
                .addAllContextAlarm(contextAlarms);
        if (defaultAlarm != null) {
            requestb.setDefaultAlarm(defaultAlarm);
        }
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ParameterTypeInfo> removeAlarms(String parameter) {
        return setAlarms(parameter, null, Collections.emptyList());
    }

    public CompletableFuture<ParameterTypeInfo> revertAlarms(String parameter) {
        UpdateParameterRequest request = UpdateParameterRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(parameter)
                .setAction(ActionType.RESET_ALARMS)
                .build();
        CompletableFuture<ParameterTypeInfo> f = new CompletableFuture<>();
        mdbOverrideService.updateParameter(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> updateAlgorithm(String algorithm, String text) {
        UpdateAlgorithmRequest request = UpdateAlgorithmRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(algorithm)
                .setAction(UpdateAlgorithmRequest.ActionType.SET)
                .setAlgorithm(AlgorithmInfo.newBuilder().setText(text))
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        mdbOverrideService.updateAlgorithm(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> revertAlgorithm(String algorithm) {
        UpdateAlgorithmRequest request = UpdateAlgorithmRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(algorithm)
                .setAction(UpdateAlgorithmRequest.ActionType.RESET)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        mdbOverrideService.updateAlgorithm(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<CommandQueueInfo> enableQueue(String queue) {
        EnableQueueRequest.Builder request = EnableQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setQueue(queue);
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.enableQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<CommandQueueInfo> disableQueue(String queue) {
        DisableQueueRequest.Builder request = DisableQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setQueue(queue);
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.disableQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<CommandQueueInfo> blockQueue(String queue) {
        BlockQueueRequest.Builder request = BlockQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setQueue(queue);
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.blockQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> rejectCommand(String queue, String commandId) {
        RejectCommandRequest.Builder request = RejectCommandRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setQueue(queue)
                .setCommand(commandId);
        CompletableFuture<Empty> f = new CompletableFuture<>();
        queueService.rejectCommand(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> acceptCommand(String queue, String commandId) {
        AcceptCommandRequest.Builder request = AcceptCommandRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setQueue(queue)
                .setCommand(commandId);
        CompletableFuture<Empty> f = new CompletableFuture<>();
        queueService.acceptCommand(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public static class CommandBuilder {
        private CommandsApiClient commandService;
        private IssueCommandRequest.Builder requestb;
        private Struct.Builder argsb;
        private int sizeOfLastCommandIssued;

        CommandBuilder(ProcessorClient client, String command) {
            this(client.commandService, client.instance, client.processor, command);
        }

        private CommandBuilder(CommandsApiClient commandService, String instance, String processor, String command) {
            this.commandService = commandService;
            requestb = IssueCommandRequest.newBuilder()
                    .setInstance(instance)
                    .setProcessor(processor)
                    .setName(command);
            argsb = Struct.newBuilder();
        }

        public CommandBuilder withArgument(String name, Object value) {
            argsb.putFields(name, WellKnownTypes.toValue(value));
            return this;
        }

        public CommandBuilder withSequenceNumber(int sequenceNumber) {
            requestb.setSequenceNumber(sequenceNumber);
            return this;
        }

        public CommandBuilder withOrigin(String origin) {
            requestb.setOrigin(origin);
            return this;
        }

        public CommandBuilder withComment(String comment) {
            requestb.setComment(comment);
            return this;
        }

        public CommandBuilder withDisableTransmissionConstraints() {
            requestb.setDisableTransmissionConstraints(true);
            return this;
        }

        public CommandBuilder withDisableVerification() {
            requestb.setDisableVerifiers(true);
            return this;
        }

        public CommandBuilder withVerifierConfig(String verifier, VerifierConfig config) {
            requestb.putVerifierConfig(verifier, config);
            return this;
        }

        public CommandBuilder withDryRun(boolean dryRun) {
            requestb.setDryRun(dryRun);
            return this;
        }

        public CommandBuilder withExtra(String option, Value value) {
            requestb.putExtra(option, value);
            return this;
        }

        /**
         * Issue the command, and returns a future that awaits the initial response.
         */
        public CompletableFuture<Command> issue() {
            var f = new CompletableFuture<IssueCommandResponse>();
            var request = requestb.setArgs(argsb).build();
            sizeOfLastCommandIssued = request.getSerializedSize();
            commandService.issueCommand(null, request, new ResponseObserver<>(f));
            return f.thenApply(Command::new);
        }

        /**
         * Issue the command, and returns a future that awaits the initial response on the specified executor.
         * <p>
         * A use case for this is to provide a single-threaded executor, thereby guaranteeing in-order delivery.
         */
        public CompletableFuture<Command> issue(Executor executor) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return issue().get();
                } catch (ExecutionException e) {
                    throw new CompletionException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, executor);
        }

        /**
         * Can be called just after issue() has been called to get the size of the last command issued
         * <p>
         * not thread safe
         */
        public int getSizeOfTheLastCommandIssued() {
            return sizeOfLastCommandIssued;
        }

        @Override
        public String toString() {
            return "CommandBuilder [commandService=" + commandService + ", requestb=" + requestb + ", argsb=" + argsb
                    + "]";
        }

    }

    public static final class GetOptions {

        public static interface GetOption {
        }

        public static GetOption fromCache(boolean fromCache) {
            return new FromCacheOption(fromCache);
        }

        public static GetOption timeout(long timeout) {
            return new TimeoutOption(timeout);
        }

        static final class FromCacheOption implements GetOption {
            final boolean fromCache;

            public FromCacheOption(boolean fromCache) {
                this.fromCache = fromCache;
            }
        }

        static final class TimeoutOption implements GetOption {
            final long timeout;

            public TimeoutOption(long timeout) {
                this.timeout = timeout;
            }
        }
    }

}
