package org.yamcs.web.rest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YamcsException;
import org.yamcs.api.Constants;
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

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

/**
 * Handles incoming requests related to Commanding (offset /commanding).
 */
public class CommandingRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(CommandingRequestHandler.class);

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
        org.yamcs.YProcessor yamcsChannel = org.yamcs.YProcessor.getInstance(yamcsInstance, "realtime");
        if (!yamcsChannel.hasCommanding()) {
            BadRequestException e = new BadRequestException("Commanding not activated for this channel");
            log.warn("Throwing bad request: {}", e.getMessage());
            throw e;
        } else {
            QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
            if (Constants.CMD_queue.equals(qsDecoder.path())) {
                if (req.getMethod() == HttpMethod.POST) {
                    RestSendCommandRequest request = readMessage(req, SchemaRest.RestSendCommandRequest.MERGE).build();
                    RestSendCommandResponse response = sendCommand(request, yamcsChannel);
                    writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestSendCommandResponse.WRITE);
                } else {
                    throw new MethodNotAllowedException(req.getMethod());
                }
            } else if (Constants.CMD_validator.equals(qsDecoder.path())) {
                if (req.getMethod() == HttpMethod.POST) {
                    RestValidateCommandRequest request = readMessage(req, SchemaRest.RestValidateCommandRequest.MERGE).build();
                    RestValidateCommandResponse response = validateCommand(request, yamcsChannel);
                    writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestValidateCommandResponse.WRITE);
                } else {
                    throw new MethodNotAllowedException(req.getMethod());
                }
            } else {
        	log.warn("Invalid path called: '{}'", qsDecoder.path());
                sendError(ctx, NOT_FOUND);
            }
        }
    }

    /**
     * Validates commands
     */
    private RestValidateCommandResponse validateCommand(RestValidateCommandRequest request, org.yamcs.YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestValidateCommandResponse.Builder responseb = RestValidateCommandResponse.newBuilder();

        for (RestCommandType restCommand : request.getCommandsList()) {
            MetaCommand mc = xtcedb.getMetaCommand(restCommand.getId());
            if(mc==null) {
            	throw new BadRequestException("Unknown command: "+restCommand.getId());
            }
            List<ArgumentAssignment> assignments = new ArrayList<>();
            for (RestArgumentType restArgument : restCommand.getArgumentsList()) {
                assignments.add(new ArgumentAssignment(restArgument.getName(), restArgument.getValue()));
            }

            String origin = required(restCommand.getOrigin(), "Origin needs to be specified");
            int seqId = restCommand.getSequenceNumber(); // will default to 0 if not set, which is fine for validation
            String user = "anonymous"; // TODO
            try {
                yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, user);
            } catch (NoPermissionException e) {
                throw new ForbiddenException(e);
            } catch (ErrorInCommand e) {
                throw new BadRequestException(e);
            } catch (YamcsException e) { // could be anything, consider as internal server error
                throw new InternalServerErrorException(e);
            }
        }

        return responseb.build();
    }

    /**
     * Validates and sends commands
     */
    private RestSendCommandResponse sendCommand(RestSendCommandRequest request, org.yamcs.YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestSendCommandResponse.Builder responseb = RestSendCommandResponse.newBuilder();

        // Validate all first
        List<PreparedCommand> validated = new ArrayList<>();
        for (RestCommandType restCommand : request.getCommandsList()) {
            MetaCommand mc = required(xtcedb.getMetaCommand(restCommand.getId()), "Unknown command: " + restCommand.getId());
            List<ArgumentAssignment> assignments = new ArrayList<>();
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
            	//make the source - should perhaps come from the client
                StringBuilder sb = new StringBuilder();
                sb.append(restCommand.getId().getName());
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
                cmd.setSource(sb.toString());
        		
                validated.add(cmd);
            } catch (NoPermissionException e) {
                throw new ForbiddenException(e);
            } catch (ErrorInCommand e) {
                throw new BadRequestException(e);
            } catch (YamcsException e) { // could be anything, consider as internal server error
                throw new InternalServerErrorException(e);
            }
        }

        // Good, now send
        for (PreparedCommand cmd : validated) {
            yamcsChannel.getCommandingManager().sendCommand(cmd);
        }

        return responseb.build();
    }
}
