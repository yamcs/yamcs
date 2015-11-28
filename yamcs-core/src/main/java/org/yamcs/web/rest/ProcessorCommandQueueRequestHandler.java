package org.yamcs.web.rest;

import org.yamcs.YProcessor;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Rest.ListCommandQueuesResponse;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaRest;

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
        } else if (!req.hasPathSegment(pathOffset + 1)) {
            String name = req.getPathSegment(pathOffset);
            return getQueue(req, name);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse listQueues(RestRequest req) throws RestException {
        ListCommandQueuesResponse.Builder response = ListCommandQueuesResponse.newBuilder();
        ManagementService managementService = ManagementService.getInstance();
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        CommandQueueManager mgr = managementService.getCommandQueueManager(processor);
        mgr.getQueues().forEach(q -> response.addQueue(toCommandQueueInfo(req, q)));
        return new RestResponse(req, response.build(), SchemaRest.ListCommandQueuesResponse.WRITE);
    }
    
    private RestResponse getQueue(RestRequest req, String name) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        YProcessor yprocessor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        if (instance == null || yprocessor == null) {
            throw new NotFoundException(req);
        }
        
        ManagementService managementService = ManagementService.getInstance();
        CommandQueueManager mgr = managementService.getCommandQueueManager(yprocessor);
        if (mgr == null) throw new BadRequestException("Commanding not enabled for this processor");
        
        CommandQueue queue = mgr.getQueue(name);
        if (queue == null) throw new NotFoundException(req, "No queue named '" + name + "'");
        
        CommandQueueInfo info = toCommandQueueInfo(req, queue);
        return new RestResponse(req, info, SchemaCommanding.CommandQueueInfo.WRITE);
    }

    private CommandQueueInfo toCommandQueueInfo(RestRequest req, CommandQueue queue) {
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder();
        b.setInstance(queue.getChannel().getInstance());
        b.setProcessorName(queue.getChannel().getName());
        b.setName(queue.getName());
        switch (queue.getState()) {
        case BLOCKED:
            b.setState(QueueState.BLOCKED);
            break;
        case DISABLED:
            b.setState(QueueState.DISABLED);
            break;
        case ENABLED:
            b.setState(QueueState.ENABLED);
            break;
        default:
            throw new IllegalStateException("Unexpected queue state " + queue.getState());
        }
        b.setNbSentCommands(queue.getNbSentCommands());
        b.setNbRejectedCommands(queue.getNbRejectedCommands());
        if (queue.getStateExpirationRemainingS() != -1) {
            b.setStateExpirationTimeS(queue.getStateExpirationRemainingS());
        }
        b.setUrl(req.getApiURL() + "/processors/" + queue.getChannel().getInstance()
                + "/" + queue.getChannel().getName() + "/cqueues/" + queue.getName());
        return b.build();
    }
}
