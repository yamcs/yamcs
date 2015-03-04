package org.yamcs.web.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.xml.XtceAliasSet;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles incoming requests related to Commanding (offset /commanding).
 */
public class CommandingRequestHandler extends AbstractRequestHandler {

    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws Exception {
        org.yamcs.Channel yamcsChannel = org.yamcs.Channel.getInstance(yamcsInstance, "realtime");
        if (!yamcsChannel.hasCommanding()) {
            System.out.println("oops");
            sendError(ctx, BAD_REQUEST);
        } else {
            System.out.println("bb");
            XtceDb xtcedb = yamcsChannel.xtcedb;

            Collection<MetaCommand> cmds = xtcedb.getMetaCommands();
            for (MetaCommand amc : cmds) {
                System.out.println("---");
                System.out.println("- " + amc.getQualifiedName() + " (" + amc.getOpsName());
                XtceAliasSet xset = amc.getAliasSet();
                for (String namespace : xset.getNamespaces()) {
                    System.out.println("Have " + namespace + ", " + xset.getAlias(namespace));
                }
                System.out.println("available mc: " + amc.getQualifiedName());
            }

            MetaCommand mc = xtcedb.getMetaCommand(NamedObjectId.newBuilder().setName("/REFMDB/SOAR/SWITCH_VOLTAGE_ON").build());
            ArgumentAssignment assignment = new ArgumentAssignment("vlotage_num", "5");

            List<ArgumentAssignment> assignments = new ArrayList<ArgumentAssignment>();
            assignments.add(assignment);
            PreparedCommand cmd = yamcsChannel.getCommandingManager().buildCommand(mc, assignments);
        }

        System.out.println("got remaining " + remainingUri);
        HttpResponse response;
        if ("validate".equals(remainingUri)) {
            System.out.println("cc");
            response = handleValidate(req, yamcsInstance);
        } else {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        Channel ch = evt.getChannel();
        ChannelFuture writeFuture = ch.write(response);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(req)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Validates commands sent by POST
     */
    private HttpResponse handleValidate(HttpRequest req, String yamcsInstance) throws Exception {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(response, AbstractRequestHandler.JSON_MIME_TYPE);
        return response;
    }
}
