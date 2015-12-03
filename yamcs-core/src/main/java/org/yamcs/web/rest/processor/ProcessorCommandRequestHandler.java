package org.yamcs.web.rest.processor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.Rest.IssueCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.StringConvertors;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.ForbiddenException;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.mdb.MDBRequestHandler;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to command info from the MDB
 */
public class ProcessorCommandRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ProcessorCommandRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            // Find out if it's a command or not. Support any namespace here. Not just XTCE
            if (req.getPathSegmentCount() - pathOffset < 2) {
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setName(lastSegment).build();
                MetaCommand cmd = mdb.getMetaCommand(id);
                if (cmd != null) { // Possibly a URL-encoded qualified name
                    req.assertPOST();
                    return issueCommand(req, id, cmd);
                } else {
                    throw new NotFoundException(req);
                }
            } else {
                String namespace = req.slicePath(pathOffset, -1);
                String rootedNamespace = "/" + namespace;
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(lastSegment).build();
                MetaCommand cmd = mdb.getMetaCommand(id);
                if (cmd != null) {
                    req.assertPOST();
                    return issueCommand(req, id, cmd);
                }
                
                id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(lastSegment).build();
                cmd = mdb.getMetaCommand(id);
                if (cmd != null) {
                    req.assertPOST();
                    return issueCommand(req, id, cmd);
                }
                
                throw new NotFoundException(req); 
            }
        }
    }
    
    private RestResponse issueCommand(RestRequest req, NamedObjectId id, MetaCommand cmd) throws RestException {
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String origin = "";
        int sequenceNumber = 0;
        boolean dryRun = false;
        List<ArgumentAssignment> assignments = new ArrayList<>();
        if (req.hasBody()) {
            IssueCommandRequest request = req.bodyAsMessage(SchemaRest.IssueCommandRequest.MERGE).build();
            if (request.hasOrigin()) origin = request.getOrigin();
            if (request.hasDryRun()) dryRun = request.getDryRun();
            if (request.hasSequenceNumber()) sequenceNumber = request.getSequenceNumber();
            for (Assignment a : request.getAssignmentList()) {
                assignments.add(new ArgumentAssignment(a.getName(), a.getValue()));
            }
        }
        
        // Override with params from query string
        for (String p : req.getQueryParameters().keySet()) {
            switch (p) {
            case "origin":
                origin = req.getQueryParameter("origin");
                break;
            case "sequenceNumber":
                sequenceNumber = req.getQueryParameterAsInt("sequenceNumber");
                break;
            case "dryRun":
                dryRun = req.getQueryParameterAsBoolean("dryRun");
                break;
            case "pretty":
                break;
            default:
                String value = req.getQueryParameter(p);
                assignments.add(new ArgumentAssignment(p, value));
            }
        }
        
        // Prepare the command
        PreparedCommand preparedCommand;
        try {
            preparedCommand = processor.getCommandingManager().buildCommand(cmd, assignments, origin, sequenceNumber, req.getAuthToken());
            
            //make the source - should perhaps come from the client
            StringBuilder sb = new StringBuilder();
            sb.append(id.getName());
            sb.append("(");
            boolean first = true;
            for(ArgumentAssignment aa:assignments) {
                if(!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(aa.getArgumentName()).append(": ").append(aa.getArgumentValue());
            }
            sb.append(")");
            preparedCommand.setSource(sb.toString());
        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }
        
        // Good, now send
        CommandQueue queue;
        if (dryRun) {
            CommandQueueManager mgr = processor.getCommandingManager().getCommandQueueManager();
            queue = mgr.getQueue(req.getAuthToken(), preparedCommand);
        } else {
            queue = processor.getCommandingManager().sendCommand(req.getAuthToken(), preparedCommand);
        }
        
        IssueCommandResponse.Builder response = IssueCommandResponse.newBuilder();
        response.setQueue(queue.getName());
        response.setSource(preparedCommand.getSource());
        response.setBinary(ByteString.copyFrom(preparedCommand.getBinary()));
        response.setHex(StringConvertors.arrayToHexString(preparedCommand.getBinary()));
        return new RestResponse(req, response.build(), SchemaRest.IssueCommandResponse.WRITE);
    }
}
