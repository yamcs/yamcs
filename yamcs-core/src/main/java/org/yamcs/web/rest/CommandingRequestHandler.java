package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.api.Constants;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandSignificance;
import org.yamcs.protobuf.Commanding.CommandSignificance.Level;
import org.yamcs.protobuf.Rest.RestArgumentType;
import org.yamcs.protobuf.Rest.RestCommandType;
import org.yamcs.protobuf.Rest.RestSendCommandRequest;
import org.yamcs.protobuf.Rest.RestSendCommandResponse;
import org.yamcs.protobuf.Rest.RestValidateCommandRequest;
import org.yamcs.protobuf.Rest.RestValidateCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to Commanding
 * <p>
 * /api/(instance)/commanding
 */
public class CommandingRequestHandler extends AbstractRestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req) throws RestException {
        YProcessor processor = YProcessor.getInstance(req.yamcsInstance, "realtime");
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        } else {
            String path = req.getRemainingUri();
            if (Constants.CMD_queue.equals(path)) {
                req.assertPOST();
                return sendCommand(req, processor);
            } else if (Constants.CMD_validator.equals(path)) {
                req.assertPOST();
                return validateCommand(req, processor);
            } else {
                throw new NotFoundException(req);
            }
        }
    }

    /**
     * Validates commands passed in request body
     * <p>
     * POST /api/(instance)/commanding/validator
     */
    private RestResponse validateCommand(RestRequest req, YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestValidateCommandRequest request = req.readMessage(SchemaRest.RestValidateCommandRequest.MERGE).build();
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
            try {
                yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, req.authToken);
            } catch (NoPermissionException e) {
                throw new ForbiddenException(e);
            } catch (ErrorInCommand e) {
                throw new BadRequestException(e);
            } catch (YamcsException e) { // could be anything, consider as internal server error
                throw new InternalServerErrorException(e);
            }
            Significance s = mc.getDefaultSignificance();
            if(s!=null) {
                CommandSignificance.Builder csb = CommandSignificance.newBuilder()
                        .setSequenceNumber(restCommand.getSequenceNumber())
                        .setConsequenceLevel(Level.valueOf(s.getConsequenceLevel().toString()))
                        .setReasonForWarning(s.getReasonForWarning());
                responseb.addCommandsSignificance(csb.build());
            }
        }

        return new RestResponse(req, responseb.build(), SchemaRest.RestValidateCommandResponse.WRITE);
    }

    /**
     * Validates and adds commands to the queue
     * <p>
     * POST /api/(instance)/commanding/queue
     */
    private RestResponse sendCommand(RestRequest req, YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestSendCommandRequest request = req.readMessage(SchemaRest.RestSendCommandRequest.MERGE).build();
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
            try {
                PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, req.authToken);
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
            yamcsChannel.getCommandingManager().sendCommand(req.authToken, cmd);
        }

        return new RestResponse(req, responseb.build(), SchemaRest.RestSendCommandResponse.WRITE);
    }
}
