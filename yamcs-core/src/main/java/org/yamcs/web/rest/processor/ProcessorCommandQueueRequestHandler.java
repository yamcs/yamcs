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
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.MethodNotAllowedException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;

/**
 * Handles requests related to command queues
 */
public class ProcessorCommandQueueRequestHandler extends RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return listQueues(req);
            } else {
                throw new MethodNotAllowedException(req);
            }
        } else {
            String queueName = req.getPathSegment(pathOffset);
            String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
            YProcessor yprocessor = req.getFromContext(RestRequest.CTX_PROCESSOR);
            if (instance == null || yprocessor == null) {
                throw new NotFoundException(req);
            }
            
            ManagementService managementService = ManagementService.getInstance();
            CommandQueueManager mgr = managementService.getCommandQueueManager(yprocessor);
            if (mgr == null) throw new BadRequestException("Commanding not enabled for this processor");
            
            CommandQueue queue = mgr.getQueue(queueName);
            if (queue == null) throw new NotFoundException(req, "No queue named '" + queueName + "'");
            
            pathOffset++;
            if (!req.hasPathSegment(pathOffset)) {
                if (req.isGET()) {
                    return getQueue(req, queue);
                } else if (req.isPATCH() || req.isPOST() || req.isPUT()) {
                    return editQueue(req, queue, mgr);
                } else {
                    throw new MethodNotAllowedException(req);
                }
            } else {
                String resource = req.getPathSegment(pathOffset);
                switch (resource) {
                case "entries":
                    pathOffset++;
                    if (!req.hasPathSegment(pathOffset)) {
                        return listQueueEntries(req, queue);
                    } else {
                        UUID entryId = UUID.fromString(req.getPathSegment(pathOffset));
                        if (req.isPATCH() || req.isPOST() || req.isPUT()) {
                            return editQueueEntry(req, entryId, mgr);
                        } else {
                            throw new MethodNotAllowedException(req);
                        }
                    }
                default:
                    throw new NotFoundException(req, "No resource '" + resource + "' for command queue " + queueName);                    
                }
            }
        }
    }
    
    private RestResponse listQueues(RestRequest req) throws RestException {
        ListCommandQueuesResponse.Builder response = ListCommandQueuesResponse.newBuilder();
        ManagementService managementService = ManagementService.getInstance();
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        CommandQueueManager mgr = managementService.getCommandQueueManager(processor);
        mgr.getQueues().forEach(q -> response.addQueue(toCommandQueueInfo(req, q, true)));
        return new RestResponse(req, response.build(), SchemaRest.ListCommandQueuesResponse.WRITE);
    }
    
    private RestResponse getQueue(RestRequest req, CommandQueue queue) throws RestException {
        CommandQueueInfo info = toCommandQueueInfo(req, queue, true);
        return new RestResponse(req, info, SchemaCommanding.CommandQueueInfo.WRITE);
    }
    
    private RestResponse editQueue(RestRequest req, CommandQueue queue, CommandQueueManager queueManager) throws RestException {
        EditCommandQueueRequest body = req.bodyAsMessage(SchemaRest.EditCommandQueueRequest.MERGE).build();
        String state = null;
        if (body.hasState()) state = body.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        
        CommandQueue updatedQueue = queue;
        if (state != null) {
            switch (state.toLowerCase()) {
            case "disabled":
                updatedQueue = queueManager.setQueueState(queue.getName(), QueueState.DISABLED);
                break;
            case "enabled":
                updatedQueue = queueManager.setQueueState(queue.getName(), QueueState.ENABLED);
                break;
            case "blocked":
                updatedQueue = queueManager.setQueueState(queue.getName(), QueueState.BLOCKED);
                break;
            default:
                throw new BadRequestException("Unsupported queue state '" + state + "'");
            }
        }
        CommandQueueInfo qinfo = toCommandQueueInfo(req, updatedQueue, true);
        return new RestResponse(req, qinfo, SchemaCommanding.CommandQueueInfo.WRITE);
    }
    
    private RestResponse listQueueEntries(RestRequest req, CommandQueue queue) throws RestException {
        ListCommandQueueEntries.Builder responseb = ListCommandQueueEntries.newBuilder();
        for (PreparedCommand pc : queue.getCommands()) {
            CommandQueueEntry qEntry = ManagementGpbHelper.toCommandQueueEntry(queue, pc);
            responseb.addEntry(qEntry);
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListCommandQueueEntries.WRITE);
    }
    
    private RestResponse editQueueEntry(RestRequest req, UUID entryId, CommandQueueManager queueManager) throws RestException {
        EditCommandQueueEntryRequest body = req.bodyAsMessage(SchemaRest.EditCommandQueueEntryRequest.MERGE).build();
        String state = null;
        if (body.hasState()) state = body.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        
        if (state != null) {
            // TODO queue manager currently iterates over all queues, which doesn't really match
            // what we want. It would be better to assure only the queue from the URI is considered.
            switch (state.toLowerCase()) {
            case "released":
                queueManager.sendCommand(entryId, false);
                break;
            case "rejected":
                String username = req.getUsername();
                queueManager.rejectCommand(entryId, username);
                break;
            default:
                throw new BadRequestException("Unsupported state '" + state + "'");
            }
        }
        
        return new RestResponse(req);
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
}
