package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Rest.ValidateCommandRequest;
import org.yamcs.protobuf.Rest.ValidateCommandResponse;
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
                ValidateCommandRequest request = readMessage(req, SchemaRest.ValidateCommandRequest.MERGE).build();
                ValidateCommandResponse response = validateCommand(request, yamcsChannel);
                writeMessage(req, qsDecoder, evt, response, SchemaRest.ValidateCommandResponse.WRITE);
            } else {
                sendError(ctx, BAD_REQUEST);
            }
        }
    }

    /**
     * Validates commands sent by POST
     */
    private ValidateCommandResponse validateCommand(ValidateCommandRequest request, org.yamcs.Channel yamcsChannel) throws Exception {
        XtceDb xtcedb = yamcsChannel.getXtceDb();

        ValidateCommandResponse.Builder responseb = ValidateCommandResponse.newBuilder();

        // TODO build from request
        MetaCommand mc = xtcedb.getMetaCommand(NamedObjectId.newBuilder().setName("/REFMDB/SOAR/SWITCH_VOLTAGE_ON").build());
        ArgumentAssignment assignment = new ArgumentAssignment("vlotage_num", "5");
        List<ArgumentAssignment> assignments = new ArrayList<ArgumentAssignment>();
        assignments.add(assignment);
        String origin = "fdi-mac"; // TODO
        int seqId = 1234; // TODO
        String user = "anonymous"; // TODO

        try {
            PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments, origin, seqId, user);
        } catch (ErrorInCommand e) {
            responseb.setException(toException("ErrorInCommand", e));
        }

        return responseb.build();
    }
}
