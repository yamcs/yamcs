package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.AbstractQueueApi;
import org.yamcs.protobuf.BlockQueueRequest;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.Commanding.CommandQueueEvent.Type;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.DisableQueueRequest;
import org.yamcs.protobuf.EditQueueEntryRequest;
import org.yamcs.protobuf.EditQueueRequest;
import org.yamcs.protobuf.EnableQueueRequest;
import org.yamcs.protobuf.GetQueueRequest;
import org.yamcs.protobuf.ListQueueEntriesRequest;
import org.yamcs.protobuf.ListQueueEntriesResponse;
import org.yamcs.protobuf.ListQueuesRequest;
import org.yamcs.protobuf.ListQueuesResponse;
import org.yamcs.protobuf.SubscribeQueueEventsRequest;
import org.yamcs.protobuf.SubscribeQueueStatisticsRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Significance.Levels;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class QueueApi extends AbstractQueueApi<Context> {

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
    public void updateQueue(Context ctx, EditQueueRequest request, Observer<CommandQueueInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        CommandQueue updatedQueue = queue;
        if (request.hasState()) {
            switch (request.getState().toLowerCase()) {
            case "disabled":
                updatedQueue = mgr.setQueueState(queue.getName(), QueueState.DISABLED);
                break;
            case "enabled":
                updatedQueue = mgr.setQueueState(queue.getName(), QueueState.ENABLED);
                break;
            case "blocked":
                updatedQueue = mgr.setQueueState(queue.getName(), QueueState.BLOCKED);
                break;
            default:
                throw new BadRequestException("Unsupported queue state '" + request.getState() + "'");
            }
        }
        int order = mgr.getQueues().indexOf(queue) + 1;
        CommandQueueInfo info = toCommandQueueInfo(updatedQueue, order, true);
        observer.complete(info);
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
    public void listQueueEntries(Context ctx, ListQueueEntriesRequest request,
            Observer<ListQueueEntriesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(mgr, request.getQueue());

        ListQueueEntriesResponse.Builder responseb = ListQueueEntriesResponse.newBuilder();
        for (ActiveCommand pc : queue.getCommands()) {
            CommandQueueEntry qEntry = toCommandQueueEntry(queue, pc);
            responseb.addEntries(qEntry);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void updateQueueEntry(Context ctx, EditQueueEntryRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        UUID entryId = UUID.fromString(request.getUuid());

        if (request.hasState()) {
            // TODO queue manager currently iterates over all queues, which doesn't really match
            // what we want. It would be better to assure only the queue from the URI is considered.
            switch (request.getState().toLowerCase()) {
            case "released":
                mgr.sendCommand(entryId);
                break;
            case "rejected":
                String username = ctx.user.getName();
                mgr.rejectCommand(entryId, username);
                break;
            default:
                throw new BadRequestException("Unsupported state '" + request.getState() + "'");
            }
        }

        observer.complete(Empty.getDefaultInstance());
    }

    private CommandQueueInfo toCommandQueueInfo(CommandQueue queue, int order, boolean detail) {
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder();
        b.setInstance(queue.getProcessor().getInstance());
        b.setProcessorName(queue.getProcessor().getName());
        b.setName(queue.getName());
        b.setState(queue.getState());
        b.setNbSentCommands(queue.getNbSentCommands());
        b.setNbRejectedCommands(queue.getNbRejectedCommands());
        b.setOrder(order);
        b.addAllUsers(queue.getUsers());
        b.addAllGroups(queue.getGroups());
        if (queue.getMinLevel() != Levels.NONE) {
            b.setMinLevel(XtceToGpbAssembler.toSignificanceLevelType(queue.getMinLevel()));
        }
        if (queue.getStateExpirationRemainingS() != -1) {
            b.setStateExpirationTimeS(queue.getStateExpirationRemainingS());
        }
        if (detail) {
            for (ActiveCommand activeCommand : queue.getCommands()) {
                CommandQueueEntry qEntry = toCommandQueueEntry(queue, activeCommand);
                b.addEntry(qEntry);
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
                .setSource(pc.getSource())
                .setPendingTransmissionConstraints(activeCommand.isPendingTransmissionConstraints())
                .setUuid(pc.getUUID().toString())
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
