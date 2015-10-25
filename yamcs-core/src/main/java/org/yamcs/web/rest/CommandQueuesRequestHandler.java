package org.yamcs.web.rest;

import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Rest.ListCommandQueuesResponse;
import org.yamcs.protobuf.SchemaRest;

/**
 * Handles requests related to command queues
 */
public class CommandQueuesRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "cqueues";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return handleListQueuesRequest(req);
            } else {
                throw new MethodNotAllowedException(req);
            }
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse handleListQueuesRequest(RestRequest req) throws RestException {
        ListCommandQueuesResponse.Builder response = ListCommandQueuesResponse.newBuilder();
        ManagementService managementService = ManagementService.getInstance();
        for (CommandQueueManager mgr : managementService.getCommandQueueManagers()) {
            if (req.getYamcsInstance() == null || req.getYamcsInstance().equals(mgr.getInstance())) {
                mgr.getQueues().forEach(q -> response.addQueue(toCommandQueueInfo(q)));
            }
        }
        return new RestResponse(req, response.build(), SchemaRest.ListCommandQueuesResponse.WRITE);
    }

    private CommandQueueInfo toCommandQueueInfo(CommandQueue queue) {
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
        return b.build();
    }
}
