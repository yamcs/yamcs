package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Rest.RestArgumentType;
import org.yamcs.protobuf.Rest.RestCommandType;
import org.yamcs.protobuf.Rest.RestValidateCommandRequest;
import org.yamcs.protobuf.Rest.RestValidateCommandResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

/**
 * Handles incoming requests related to Commanding (offset /commanding).
 */
public class CommandingRequestHandler extends RestRequestHandler {

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws Exception {
        org.yamcs.Channel yamcsChannel = org.yamcs.Channel.getInstance(yamcsInstance, "realtime");
        if (!yamcsChannel.hasCommanding()) {
            sendError(ctx, BAD_REQUEST);
        } else {
            QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
            if ("validate".equals(qsDecoder.getPath())) {
                RestValidateCommandRequest request = readMessage(req, SchemaRest.RestValidateCommandRequest.MERGE).build();
                RestValidateCommandResponse response = validateCommand(request, yamcsChannel);
                writeMessage(req, qsDecoder, evt, response, SchemaRest.RestValidateCommandResponse.WRITE);
            } else {
                sendError(ctx, BAD_REQUEST);
            }
        }
    }

    /**
     * Validates commands sent by POST
     * /REFMDB/SOAR/SWITCH_VOLTAGE_ON
     * curl -XGET http://localhost:8090/commanding/validate -D
     * vlotage_num", "5"
     */
    private RestValidateCommandResponse validateCommand(RestValidateCommandRequest request, org.yamcs.Channel yamcsChannel) throws Exception {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        RestValidateCommandResponse.Builder responseb = RestValidateCommandResponse.newBuilder();

        for (RestCommandType restCommand : request.getCommandsList()) {
            MetaCommand mc = xtcedb.getMetaCommand(NamedObjectId.newBuilder().setName(restCommand.getName().getName()).build());

            List<ArgumentAssignment> assignments = new ArrayList<ArgumentAssignment>();
            for (RestArgumentType restArgument : restCommand.getArgumentsList()) {
                assignments.add(new ArgumentAssignment(restArgument.getName(), restArgument.getValue()));
            }

            String origin = "fdi-mac"; // TODO
            int seqId = 1234; // TODO
            String user = "anonymous"; // TODO

            try {
                PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, user);
            } catch (ErrorInCommand e) {
                responseb.setException(toException("ErrorInCommand", e));
            }

            break; // FIXME
        }

        return responseb.build();
    }
}
