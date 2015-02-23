package org.yamcs.web;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.dyuproject.protostuff.JsonIOUtil;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles incoming requests related to the MDB (offset /mdb).
 */
public class MdbRequestHandler extends AbstractRequestHandler {

    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws Exception {
        String contentType = JSON_MIME_TYPE; // Default to json
        if (req.containsHeader(HttpHeaders.Names.ACCEPT)) {
            String accept = req.getHeader(HttpHeaders.Names.ACCEPT);
            if (BINARY_MIME_TYPE.equals(accept)) {
                contentType = BINARY_MIME_TYPE;
            }
        }
        HttpResponse response;
        if ("parameters".equals(remainingUri)) {
            response = handleParameters(yamcsInstance, contentType);
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
     * Sends the XTCEDB for the requested yamcs instance.
     * <p>
     * Currently only sends MDB:OPS Name names.
     */
    private HttpResponse handleParameters(String yamcsInstance, String contentType) throws Exception {
        XtceDb mdb =  XtceDbFactory.getInstance(yamcsInstance);

        NamedObjectList.Builder list = NamedObjectList.newBuilder();

        NamedDescriptionIndex<Parameter> index = mdb.getParameterAliases();
        for (String name : index.getNamesForAlias(MdbMappings.MDB_OPSNAME)) {
            list.addList(NamedObjectId.newBuilder()
                    .setNamespace(MdbMappings.MDB_OPSNAME)
                    .setName(name));
        }

        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream out = new ChannelBufferOutputStream(buf);

        if (BINARY_MIME_TYPE.equals(contentType)) {
            NamedObjectList l = list.build();
            l.writeTo(out);
        } else {
            JsonIOUtil.writeTo(out, list.build(), SchemaYamcs.NamedObjectList.WRITE, false);
        }

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(response, contentType);
        setContentLength(response, buf.readableBytes());
        response.setContent(buf);
        return response;
    }
}
