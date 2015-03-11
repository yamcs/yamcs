package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YamcsException;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Rest.RestArgumentType;
import org.yamcs.protobuf.Rest.RestCommandType;
import org.yamcs.protobuf.Rest.RestSendCommandRequest;
import org.yamcs.protobuf.Rest.RestSendCommandResponse;
import org.yamcs.protobuf.Rest.RestValidateCommandRequest;
import org.yamcs.protobuf.Rest.RestValidateCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

/**
 * Handles incoming requests related to Commanding (offset /commanding).
 */
public class CommandingRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(CommandingRequestHandler.class);

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException {
        org.yamcs.Channel yamcsChannel = org.yamcs.Channel.getInstance(yamcsInstance, "realtime");
        if (!yamcsChannel.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this channel");
        } else {
            QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
            if ("validate".equals(qsDecoder.getPath())) {
                RestValidateCommandRequest request = readMessage(req, SchemaRest.RestValidateCommandRequest.MERGE).build();
                RestValidateCommandResponse response = validateCommand(request, yamcsChannel);
                writeMessage(req, qsDecoder, evt, response, SchemaRest.RestValidateCommandResponse.WRITE);
            } else if ("send".equals(qsDecoder.getPath())) {
                RestSendCommandRequest request = readMessage(req, SchemaRest.RestSendCommandRequest.MERGE).build();
                RestSendCommandResponse response = sendCommand(request, yamcsChannel);
                writeMessage(req, qsDecoder, evt, response, SchemaRest.RestSendCommandResponse.WRITE);
            } else {
                sendError(ctx, NOT_FOUND);
            }
        }
    }

    /**
     * Validates commands sent by POST
     */
    private RestValidateCommandResponse validateCommand(RestValidateCommandRequest request, org.yamcs.Channel yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestValidateCommandResponse.Builder responseb = RestValidateCommandResponse.newBuilder();

        for (RestCommandType restCommand : request.getCommandsList()) {
            MetaCommand mc = xtcedb.getMetaCommand(restCommand.getId());
            if(mc==null) {
            	throw new BadRequestException("Unknown command: "+restCommand.getId());
            }
            List<ArgumentAssignment> assignments = new ArrayList<ArgumentAssignment>();
            for (RestArgumentType restArgument : restCommand.getArgumentsList()) {
                assignments.add(new ArgumentAssignment(restArgument.getName(), restArgument.getValue()));
            }

            String origin = required(restCommand.getOrigin(), "Origin needs to be specified");
            int seqId = restCommand.getSequenceNumber(); // will default to 0 if not set, which is fine for validation
            String user = "anonymous"; // TODO
            try {
                PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, user);
            } catch (NoPermissionException e) {
                throw new ForbiddenException(e);
            } catch (ErrorInCommand e) {
                throw new BadRequestException(e);
            } catch (YamcsException e) { // could be anything, consider as internal server error
                log.error("Could not build command", e);
                throw new RestException(e);
            }
        }

        return responseb.build();
    }

    /**
     * Validates and sends commands sent by POST
     */
    private RestSendCommandResponse sendCommand(RestSendCommandRequest request, org.yamcs.Channel yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestSendCommandResponse.Builder responseb = RestSendCommandResponse.newBuilder();

        // Validate all first
        List<PreparedCommand> validated = new ArrayList<PreparedCommand>();
        for (RestCommandType restCommand : request.getCommandsList()) {
            MetaCommand mc = required(xtcedb.getMetaCommand(restCommand.getId()), "Unknown command: " + restCommand.getId());
            List<ArgumentAssignment> assignments = new ArrayList<ArgumentAssignment>();
            for (RestArgumentType restArgument : restCommand.getArgumentsList()) {
                assignments.add(new ArgumentAssignment(restArgument.getName(), restArgument.getValue()));
            }

            String origin = required(restCommand.getOrigin(), "Origin needs to be specified");
            if (!restCommand.hasSequenceNumber()) {
                throw new BadRequestException("SequenceNumber needs to be specified");
            }
            int seqId = restCommand.getSequenceNumber();
            String user = "anonymous"; // TODO
            try {
                PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, user);
                validated.add(cmd);
            } catch (NoPermissionException e) {
                throw new ForbiddenException(e);
            } catch (ErrorInCommand e) {
                throw new BadRequestException(e);
            } catch (YamcsException e) { // could be anything, consider as internal server error
                log.error("Could not build command", e);
                throw new RestException(e);
            }
        }

        // Good, now send
        for (PreparedCommand cmd : validated) {
            yamcsChannel.getCommandingManager().sendCommand(cmd);
        }

        return responseb.build();
    }
}
