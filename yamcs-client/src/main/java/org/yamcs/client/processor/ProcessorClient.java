package org.yamcs.client.processor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.Helpers;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.processor.ProcessorClient.GetOptions.FromCacheOption;
import org.yamcs.client.processor.ProcessorClient.GetOptions.GetOption;
import org.yamcs.client.processor.ProcessorClient.GetOptions.TimeoutOption;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.CommandsApiClient;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.EditQueueEntryRequest;
import org.yamcs.protobuf.EditQueueRequest;
import org.yamcs.protobuf.GetParameterValueRequest;
import org.yamcs.protobuf.GetProcessorRequest;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.UpdateAlgorithmRequest;
import org.yamcs.protobuf.Mdb.UpdateParameterRequest;
import org.yamcs.protobuf.Mdb.UpdateParameterRequest.ActionType;
import org.yamcs.protobuf.MdbApiClient;
import org.yamcs.protobuf.ProcessingApiClient;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.QueueApiClient;
import org.yamcs.protobuf.SetParameterValueRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.Yamcs.Value;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class ProcessorClient {

    private String instance;
    private String processor;

    private ProcessingApiClient processingService;
    private CommandsApiClient commandService;
    private MdbApiClient mdbService;
    private QueueApiClient queueService;

    public ProcessorClient(MethodHandler handler, String instance, String processor) {
        this.instance = instance;
        this.processor = processor;
        processingService = new ProcessingApiClient(handler);
        commandService = new CommandsApiClient(handler);
        mdbService = new MdbApiClient(handler);
        queueService = new QueueApiClient(handler);
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

    public CompletableFuture<IssueCommandResponse> issueCommand(String command, Map<String, ?> arguments) {
        IssueCommandRequest.Builder requestb = IssueCommandRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(command);
        for (Entry<String, ?> entry : arguments.entrySet()) {
            String stringValue = String.valueOf(entry.getValue());
            requestb.addAssignment(Assignment.newBuilder()
                    .setName(entry.getKey())
                    .setValue(stringValue));
        }
        CompletableFuture<IssueCommandResponse> f = new CompletableFuture<>();
        commandService.issueCommand(null, requestb.build(), new ResponseObserver<>(f));
        return f;
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
        mdbService.updateParameter(null, request, new ResponseObserver<>(f));
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
        mdbService.updateParameter(null, requestb.build(), new ResponseObserver<>(f));
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
        mdbService.updateParameter(null, request, new ResponseObserver<>(f));
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
        mdbService.updateParameter(null, request, new ResponseObserver<>(f));
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
        mdbService.updateParameter(null, requestb.build(), new ResponseObserver<>(f));
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
        mdbService.updateParameter(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> updateAlgorithm(String algorithm, AlgorithmInfo definition) {
        UpdateAlgorithmRequest request = UpdateAlgorithmRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(algorithm)
                .setAction(UpdateAlgorithmRequest.ActionType.SET)
                .setAlgorithm(definition)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        mdbService.updateAlgorithm(null, request, new ResponseObserver<>(f));
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
        mdbService.updateAlgorithm(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<CommandQueueInfo> enableQueue(String queue) {
        EditQueueRequest.Builder request = EditQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(queue)
                .setState("enabled");
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.updateQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<CommandQueueInfo> disableQueue(String queue) {
        EditQueueRequest.Builder request = EditQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(queue)
                .setState("disabled");
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.updateQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<CommandQueueInfo> blockQueue(String queue) {
        EditQueueRequest.Builder request = EditQueueRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(queue)
                .setState("blocked");
        CompletableFuture<CommandQueueInfo> f = new CompletableFuture<>();
        queueService.updateQueue(null, request.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> rejectQueueEntry(String queue, String uuid) {
        EditQueueEntryRequest.Builder request = EditQueueEntryRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(queue)
                .setUuid(uuid)
                .setState("rejected");
        CompletableFuture<Empty> f = new CompletableFuture<>();
        queueService.updateQueueEntry(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> releaseQueueEntry(String queue, String uuid) {
        EditQueueEntryRequest.Builder request = EditQueueEntryRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .setName(queue)
                .setUuid(uuid)
                .setState("released");
        CompletableFuture<Empty> f = new CompletableFuture<>();
        queueService.updateQueueEntry(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public static class CommandBuilder {

        private CommandsApiClient commandService;
        private IssueCommandRequest.Builder requestb;

        private CommandBuilder(CommandsApiClient commandService, String instance, String processor, String command) {
            this.commandService = commandService;
            requestb = IssueCommandRequest.newBuilder()
                    .setInstance(instance)
                    .setProcessor(processor)
                    .setName(command);
        }

        public CommandBuilder withArgument(String name, long value) {
            requestb.addAssignment(Assignment.newBuilder()
                    .setName(name)
                    .setValue(Long.toString(value)));
            return this;
        }

        public CommandBuilder withArgument(String name, String value) {
            requestb.addAssignment(Assignment.newBuilder()
                    .setName(name)
                    .setValue(value));
            return this;
        }

        public CommandBuilder withArgument(String name, boolean value) {
            requestb.addAssignment(Assignment.newBuilder()
                    .setName(name)
                    .setValue(Boolean.toString(value)));
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

        public CompletableFuture<IssueCommandResponse> issue() {
            CompletableFuture<IssueCommandResponse> f = new CompletableFuture<>();
            commandService.issueCommand(null, requestb.build(), new ResponseObserver<>(f));
            return f;
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
