package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.api.Constants;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.ArgumentAssignmentType;
import org.yamcs.protobuf.Commanding.CommandSignificance;
import org.yamcs.protobuf.Commanding.CommandType;
import org.yamcs.protobuf.Mdb.SignificanceInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.Rest.SendCommandRequest;
import org.yamcs.protobuf.Rest.ValidateCommandRequest;
import org.yamcs.protobuf.Rest.ValidateCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to Commanding
 * <p>
 * /(instance)/commanding
 */
public class CommandingRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "commanding";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }
        
        switch (req.getPathSegment(pathOffset)) {
            case Constants.CMD_queue:
                req.assertPOST();
                return sendCommand(req, processor);

            case Constants.CMD_validator:
                req.assertPOST();
                return validateCommand(req, processor);

            default:
                throw new NotFoundException(req);
        }
    }

    /**
     * Validates commands passed in request body
     * <p>
     * POST /api/:instance/commanding/validator
     */
    private RestResponse validateCommand(RestRequest req, YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        ValidateCommandRequest request = req.bodyAsMessage(SchemaRest.ValidateCommandRequest.MERGE).build();
        ValidateCommandResponse.Builder responseb = ValidateCommandResponse.newBuilder();
        
        for (CommandType restCommand : request.getCommandList()) {
            MetaCommand mc = xtcedb.getMetaCommand(restCommand.getId());
            if(mc==null) {
                throw new BadRequestException("Unknown command: "+restCommand.getId());
            }
            List<ArgumentAssignment> assignments = new ArrayList<>();
            for (ArgumentAssignmentType restArgument : restCommand.getArgumentsList()) {
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
                SignificanceInfo.Builder sib = SignificanceInfo.newBuilder();
                sib.setConsequenceLevel(SignificanceLevelType.valueOf(s.getConsequenceLevel().toString().toUpperCase()));
                sib.setReasonForWarning(s.getReasonForWarning());
                
                CommandSignificance.Builder csb = CommandSignificance.newBuilder()
                        .setSequenceNumber(restCommand.getSequenceNumber())
                        .setSignificance(sib);
                responseb.addCommandSignificance(csb.build());
            }
        }

        return new RestResponse(req, responseb.build(), SchemaRest.ValidateCommandResponse.WRITE);
    }

    /**
     * Validates and adds commands to the queue
     * <p>
     * POST /api/:instance/commanding/queue
     */
    private RestResponse sendCommand(RestRequest req, YProcessor yamcsChannel) throws RestException {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        SendCommandRequest request = req.bodyAsMessage(SchemaRest.SendCommandRequest.MERGE).build();

        // Validate all first
        List<PreparedCommand> validated = new ArrayList<>();
        for (CommandType restCommand : request.getCommandList()) {
            MetaCommand mc = required(xtcedb.getMetaCommand(restCommand.getId()), "Unknown command: " + restCommand.getId());
            List<ArgumentAssignment> assignments = new ArrayList<>();
            for (ArgumentAssignmentType restArgument : restCommand.getArgumentsList()) {
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

        return new RestResponse(req);
    }
}
