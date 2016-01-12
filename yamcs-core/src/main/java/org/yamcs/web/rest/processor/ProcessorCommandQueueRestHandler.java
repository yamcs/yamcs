package org.yamcs.web.rest.processor;

import java.util.UUID;

import org.yamcs.YProcessor;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Rest.EditCommandQueueEntryRequest;
import org.yamcs.protobuf.Rest.EditCommandQueueRequest;
import org.yamcs.protobuf.Rest.ListCommandQueueEntries;
import org.yamcs.protobuf.Rest.ListCommandQueuesResponse;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.Route;

import io.netty.channel.ChannelFuture;


public class ProcessorCommandQueueRestHandler extends RestHandler {
    
    @Route(path = "/api/processors/:instance/:processor/cqueues", method = "GET")
    public ChannelFuture listQueues(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        ListCommandQueuesResponse.Builder response = ListCommandQueuesResponse.newBuilder();
        ManagementService managementService = ManagementService.getInstance();
        CommandQueueManager mgr = managementService.getCommandQueueManager(processor);
        mgr.getQueues().forEach(q -> response.addQueue(toCommandQueueInfo(req, q, true)));
        return sendOK(req, response.build(), SchemaRest.ListCommandQueuesResponse.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor/cqueues/:name", method = "GET")
    public ChannelFuture getQueue(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(req, mgr, req.getRouteParam("name"));
        
        CommandQueueInfo info = toCommandQueueInfo(req, queue, true);
        return sendOK(req, info, SchemaCommanding.CommandQueueInfo.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor/cqueues/:name", method = { "PATCH", "PUT", "POST" })
    public ChannelFuture editQueue(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(req, mgr, req.getRouteParam("name"));
        
        EditCommandQueueRequest body = req.bodyAsMessage(SchemaRest.EditCommandQueueRequest.MERGE).build();
        String state = null;
        if (body.hasState()) state = body.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        
        CommandQueue updatedQueue = queue;
        if (state != null) {
            switch (state.toLowerCase()) {
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
                throw new BadRequestException("Unsupported queue state '" + state + "'");
            }
        }
        CommandQueueInfo qinfo = toCommandQueueInfo(req, updatedQueue, true);
        return sendOK(req, qinfo, SchemaCommanding.CommandQueueInfo.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor/cqueues/:name/entries", method = "GET")
    public ChannelFuture listQueueEntries(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        CommandQueue queue = verifyCommandQueue(req, mgr, req.getRouteParam("name"));
        
        ListCommandQueueEntries.Builder responseb = ListCommandQueueEntries.newBuilder();
        for (PreparedCommand pc : queue.getCommands()) {
            CommandQueueEntry qEntry = ManagementGpbHelper.toCommandQueueEntry(queue, pc);
            responseb.addEntry(qEntry);
        }
        return sendOK(req, responseb.build(), SchemaRest.ListCommandQueueEntries.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor/cqueues/:cqueue/entries/:uuid", method = { "PATCH", "PUT", "POST" })
    public ChannelFuture editQueueEntry(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        CommandQueueManager mgr = verifyCommandQueueManager(processor);
        // CommandQueue queue = verifyCommandQueue(req, mgr, req.getRouteParam("cqueue"));
        UUID entryId = UUID.fromString(req.getRouteParam("uuid"));
        
        EditCommandQueueEntryRequest body = req.bodyAsMessage(SchemaRest.EditCommandQueueEntryRequest.MERGE).build();
        String state = null;
        if (body.hasState()) state = body.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        
        if (state != null) {
            // TODO queue manager currently iterates over all queues, which doesn't really match
            // what we want. It would be better to assure only the queue from the URI is considered.
            switch (state.toLowerCase()) {
            case "released":
                mgr.sendCommand(entryId, false);
                break;
            case "rejected":
                String username = req.getUsername();
                mgr.rejectCommand(entryId, username);
                break;
            default:
                throw new BadRequestException("Unsupported state '" + state + "'");
            }
        }
        
        return sendOK(req);
    }

    private CommandQueueInfo toCommandQueueInfo(RestRequest req, CommandQueue queue, boolean detail) {
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder();
        b.setInstance(queue.getChannel().getInstance());
        b.setProcessorName(queue.getChannel().getName());
        b.setName(queue.getName());
        b.setState(queue.getState());
        b.setNbSentCommands(queue.getNbSentCommands());
        b.setNbRejectedCommands(queue.getNbRejectedCommands());
        if (queue.getStateExpirationRemainingS() != -1) {
            b.setStateExpirationTimeS(queue.getStateExpirationRemainingS());
        }
        if (detail) {
            for (PreparedCommand pc : queue.getCommands()) {
                CommandQueueEntry qEntry = ManagementGpbHelper.toCommandQueueEntry(queue, pc);
                b.addEntry(qEntry);
            }
        }
        if (!req.getOptions().contains(Option.NO_LINK)) {
            b.setUrl(req.getApiURL() + "/processors/" + queue.getChannel().getInstance()
                    + "/" + queue.getChannel().getName() + "/cqueues/" + queue.getName());
        }
        return b.build();
    }
    
    private CommandQueueManager verifyCommandQueueManager(YProcessor processor) throws BadRequestException {
        ManagementService managementService = ManagementService.getInstance();
        CommandQueueManager mgr = managementService.getCommandQueueManager(processor);
        if (mgr == null) throw new BadRequestException("Commanding not enabled for processor '" + processor.getName() + "'");
        return mgr;
    }
    
    private CommandQueue verifyCommandQueue(RestRequest req, CommandQueueManager mgr, String queueName) throws NotFoundException {
        CommandQueue queue = mgr.getQueue(queueName);
        if (queue == null) {
            String processorName = mgr.getChannelName();
            String instance = mgr.getInstance();
            throw new NotFoundException(req, "No queue named '" + queueName + "' (processor: '" + instance + "/" + processorName + "')");
        } else {
            return queue;
        }
    }
}
