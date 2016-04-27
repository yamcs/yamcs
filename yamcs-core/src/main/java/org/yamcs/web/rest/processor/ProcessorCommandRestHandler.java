package org.yamcs.web.rest.processor;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.protobuf.Commanding;
import org.yamcs.protobuf.Rest;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.Rest.IssueCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.utils.StringConverter;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.*;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

import io.netty.channel.ChannelFuture;
import org.yaml.snakeyaml.util.UriEncoder;

/**
 * Processes command requests
 */
public class ProcessorCommandRestHandler extends RestHandler {
    
    @Route(path = "/api/processors/:instance/:processor/commands/:name*", method = "POST")
    public ChannelFuture issueCommand(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String requestCommandName = UriEncoder.decode(req.getRouteParam("name"));
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        MetaCommand cmd = verifyCommand(req, mdb, requestCommandName);


        String origin = "";
        int sequenceNumber = 0;
        boolean dryRun = false;
        String comment = null;
        List<ArgumentAssignment> assignments = new ArrayList<>();
        if (req.hasBody()) {
            IssueCommandRequest request = req.bodyAsMessage(SchemaRest.IssueCommandRequest.MERGE).build();
            if (request.hasOrigin()) origin = request.getOrigin();
            if (request.hasDryRun()) dryRun = request.getDryRun();
            if (request.hasSequenceNumber()) sequenceNumber = request.getSequenceNumber();
            if (request.hasComment()) comment = request.getComment();
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
            case "nolink":
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
            sb.append(requestCommandName);
            sb.append("(");
            boolean first = true;
            for(ArgumentAssignment aa:assignments) {
                Argument a = preparedCommand.getMetaCommand().getArgument(aa.getArgumentName());
                if(!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(aa.getArgumentName()).append(": ");

                boolean needDelimiter = a !=null && (a.getArgumentType() instanceof StringArgumentType || a.getArgumentType() instanceof EnumeratedArgumentType);
                if(needDelimiter) {
                    sb.append("\"");
                }
                sb.append(aa.getArgumentValue());
                if(needDelimiter) {
                    sb.append("\"");
                }
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
            if(comment != null) {
                try {
                    processor.getCommandingManager().addToCommandHistory(preparedCommand.getCommandId(), "Comment", comment, req.getAuthToken());
                } catch (NoPermissionException e) {
                    throw new ForbiddenException(e);
                }
            }
        }

        Commanding.CommandQueueEntry cqe = ManagementGpbHelper.toCommandQueueEntry(queue, preparedCommand);
        
        IssueCommandResponse.Builder response = IssueCommandResponse.newBuilder();
        response.setCommandQueueEntry(cqe);
        response.setSource(preparedCommand.getSource());
        response.setBinary(ByteString.copyFrom(preparedCommand.getBinary()));
        response.setHex(StringConverter.arrayToHexString(preparedCommand.getBinary()));
        return sendOK(req, response.build(), SchemaRest.IssueCommandResponse.WRITE);
    }

    @Route(path = "/api/processors/:instance/:processor/commandhistory/:name*", method = "POST")
    public ChannelFuture updateCommandHistory(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        try {
            if (req.hasBody()) {
                Rest.UpdateCommandHistoryRequest request = req.bodyAsMessage(SchemaRest.UpdateCommandHistoryRequest.MERGE).build();
                Commanding.CommandId cmdId = request.getCmdId();

                for (Rest.UpdateCommandHistoryRequest.KeyValue historyEntry : request.getHistoryEntryList()) {
                    processor.getCommandingManager().addToCommandHistory(cmdId, historyEntry.getKey(), historyEntry.getValue(), req.getAuthToken());
                }
            }
        }
        catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }

        return sendOK(req);
    }
}
