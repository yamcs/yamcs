package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.api.Observer;
import org.yamcs.commanding.ActiveCommand;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.AbstractQueuesApi;
import org.yamcs.protobuf.AcceptCommandRequest;
import org.yamcs.protobuf.BlockQueueRequest;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.Commanding.CommandQueueEvent.Type;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.DisableQueueRequest;
import org.yamcs.protobuf.EnableQueueRequest;
import org.yamcs.protobuf.GetQueueRequest;
import org.yamcs.protobuf.ListQueuedCommandsRequest;
import org.yamcs.protobuf.ListQueuedCommandsResponse;
import org.yamcs.protobuf.ListQueuesRequest;
import org.yamcs.protobuf.ListQueuesResponse;
import org.yamcs.protobuf.RejectCommandRequest;
import org.yamcs.protobuf.SubscribeQueueEventsRequest;
import org.yamcs.protobuf.SubscribeQueueStatisticsRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Significance.Levels;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class QueuesApi extends AbstractQueuesApi<Context> {

    private AuditLog auditLog;

    public QueuesApi(AuditLog auditLog) {
        this.auditLog = auditLog;
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ControlCommandQueue);
        });
        // Legacy name, remove eventually
        auditLog.addPrivilegeChecker("QueueApi", user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ControlCommandQueue);
        });
    }

    @Override
    public void listQueues(Context ctx, ListQueuesRequest request, Observer<ListQueuesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);

        ListQueuesResponse.Builder response = ListQueuesResponse.newBuilder();
        List<CommandQueue> queues = new ArrayList<>(mgr.getQueues()); // In definition order
        for (int i = 0; i < queues.size(); i++) {
            CommandQueue q = queues.get(i);
            response.addQueues(toCommandQueueInfo(q, i + 1, true));
        }
        observer.complete(response.build());
    }

    @Override
    public void getQueue(Context ctx, GetQueueRequest request, Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        int order = mgr.getQueues().indexOf(queue) + 1;
        CommandQueueInfo info = toCommandQueueInfo(queue, order, true);
        observer.complete(info);
    }

    @Override
    public void subscribeQueueStatistics(Context ctx, SubscribeQueueStatisticsRequest request,
            Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);

        for (CommandQueue q : mgr.getQueues()) {
            int order = mgr.getQueues().indexOf(q) + 1;
            CommandQueueInfo info = toCommandQueueInfo(q, order, true);
            observer.next(info);
        }

        CommandQueueListener listener = new CommandQueueListener() {
            @Override
            public void updateQueue(CommandQueue q) {
                int order = mgr.getQueues().indexOf(q) + 1;
                CommandQueueInfo info = toCommandQueueInfo(q, order, false);
                observer.next(info);
            }
        };
        observer.setCancelHandler(() -> mgr.removeListener(listener));
        mgr.registerListener(listener);
    }

    @Override
    public void subscribeQueueEvents(Context ctx, SubscribeQueueEventsRequest request,
            Observer<CommandQueueEvent> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);

        CommandQueueListener listener = new CommandQueueListener() {
            @Override
            public void commandAdded(CommandQueue q, ActiveCommand pc) {
                CommandQueueEntry data = toCommandQueueEntry(q, pc);
                CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
                evtb.setType(Type.COMMAND_ADDED);
                evtb.setData(data);
                observer.next(evtb.build());
            }

            @Override
            public void commandUpdated(CommandQueue q, ActiveCommand pc) {
                CommandQueueEntry data = toCommandQueueEntry(q, pc);
                CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
                evtb.setType(Type.COMMAND_UPDATED);
                evtb.setData(data);
                observer.next(evtb.build());
            }

            @Override
            public void commandRejected(CommandQueue q, ActiveCommand pc) {
                CommandQueueEntry data = toCommandQueueEntry(q, pc);
                CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
                evtb.setType(Type.COMMAND_REJECTED);
                evtb.setData(data);
                observer.next(evtb.build());
            }

            @Override
            public void commandSent(CommandQueue q, ActiveCommand pc) {
                CommandQueueEntry data = toCommandQueueEntry(q, pc);
                CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
                evtb.setType(Type.COMMAND_SENT);
                evtb.setData(data);
                observer.next(evtb.build());
            }
        };
        observer.setCancelHandler(() -> mgr.removeListener(listener));
        mgr.registerListener(listener);
    }

    @Override
    public void enableQueue(Context ctx, EnableQueueRequest request, Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        CommandQueue updatedQueue = mgr.setQueueState(queue.getName(), QueueState.ENABLED);
        int order = mgr.getQueues().indexOf(queue) + 1;
        CommandQueueInfo info = toCommandQueueInfo(updatedQueue, order, true);
        observer.complete(info);
    }

    @Override
    public void disableQueue(Context ctx, DisableQueueRequest request, Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        CommandQueue updatedQueue = mgr.setQueueState(queue.getName(), QueueState.DISABLED);
        int order = mgr.getQueues().indexOf(queue) + 1;
        CommandQueueInfo info = toCommandQueueInfo(updatedQueue, order, true);
        observer.complete(info);
    }

    @Override
    public void blockQueue(Context ctx, BlockQueueRequest request, Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        CommandQueue updatedQueue = mgr.setQueueState(queue.getName(), QueueState.BLOCKED);
        int order = mgr.getQueues().indexOf(queue) + 1;
        CommandQueueInfo info = toCommandQueueInfo(updatedQueue, order, true);
        observer.complete(info);
    }

    private CommandQueueManager verifyCommandQueueManager(Processor processor) throws BadRequestException {
        ManagementService managementService = ManagementService.getInstance();
        CommandQueueManager mgr = managementService.getCommandQueueManager(processor);
        if (mgr == null) {
            throw new BadRequestException("Commanding not enabled for processor '" + processor.getName() + "'");
        }
        return mgr;
    }

    private CommandQueue verifyCommandQueue(CommandQueueManager mgr, String queueName) throws NotFoundException {
        CommandQueue queue = mgr.getQueue(queueName);
        if (queue == null) {
            String processorName = mgr.getChannelName();
            String instance = mgr.getInstance();
            throw new NotFoundException(
                    "No queue named '" + queueName + "' (processor: '" + instance + "/" + processorName + "')");
        } else {
            return queue;
        }
    }

    @Override
    public void listQueuedCommands(Context ctx, ListQueuedCommandsRequest request,
            Observer<ListQueuedCommandsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        ListQueuedCommandsResponse.Builder responseb = ListQueuedCommandsResponse.newBuilder();
        for (ActiveCommand pc : queue.getCommands()) {
            CommandQueueEntry qEntry = toCommandQueueEntry(queue, pc);
            responseb.addCommands(qEntry);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void acceptCommand(Context ctx, AcceptCommandRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        String commandId = request.getCommand();
        PreparedCommand pc = mgr.sendCommand(commandId);

        auditLog.addRecord(ctx, request, String.format(
                "Command '%s' accepted for processor '%s' (id: %s)",
                pc.getCommandName(), processor.getName(), pc.getId()));

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void rejectCommand(Context ctx, RejectCommandRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        String commandId = request.getCommand();
        String username = ctx.user.getName();
        PreparedCommand pc = mgr.rejectCommand(commandId, username);

        auditLog.addRecord(ctx, request, String.format(
                "Command '%s' rejected for processor '%s' (id: %s)",
                pc.getCommandName(), processor.getName(), pc.getId()));

        observer.complete(Empty.getDefaultInstance());
    }

    private CommandQueueInfo toCommandQueueInfo(CommandQueue queue, int order, boolean detail) {
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder();
        b.setInstance(queue.getProcessor().getInstance());
        b.setProcessorName(queue.getProcessor().getName());
        b.setName(queue.getName());
        b.setState(queue.getState());
        b.setAcceptedCommandsCount(queue.getNbSentCommands());
        b.setRejectedCommandsCount(queue.getNbRejectedCommands());
        b.setOrder(order);
        b.addAllUsers(queue.getUsers());
        b.addAllGroups(queue.getGroups());
        var tcPatterns = new ArrayList<>(queue.getTcPatterns())
                .stream().map(p -> p.pattern())
                .sorted()
                .collect(Collectors.toList());
        b.addAllTcPatterns(tcPatterns);

        if (queue.getMinLevel() != Levels.NONE) {
            b.setMinLevel(XtceToGpbAssembler.toSignificanceLevelType(queue.getMinLevel()));
        }
        if (detail) {
            for (ActiveCommand activeCommand : queue.getCommands()) {
                CommandQueueEntry qEntry = toCommandQueueEntry(queue, activeCommand);
                b.addEntries(qEntry);
            }
        }
        return b.build();
    }

    private static CommandQueueEntry toCommandQueueEntry(CommandQueue q, ActiveCommand activeCommand) {
        Processor c = q.getProcessor();
        PreparedCommand pc = activeCommand.getPreparedCommand();
        CommandQueueEntry.Builder entryb = CommandQueueEntry.newBuilder()
                .setInstance(q.getProcessor().getInstance())
                .setProcessorName(c.getName())
                .setQueueName(q.getName())
                .setId(pc.getId())
                .setOrigin(pc.getOrigin())
                .setSequenceNumber(pc.getSequenceNumber())
                .setCommandName(pc.getCommandName())
                .setPendingTransmissionConstraints(activeCommand.isPendingTransmissionConstraints())
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(pc.getGenerationTime()))
                .setUsername(pc.getUsername())
                .addAllAssignments(pc.getAssignments());

        if (pc.getBinary() != null) {
            entryb.setBinary(ByteString.copyFrom(pc.getBinary()));
        }

        if (pc.getComment() != null) {
            entryb.setComment(pc.getComment());
        }

        return entryb.build();
    }
}
