package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * These methods are looking for a better home. A ResponseBuilder ?
 */
public class RestUtils {
    
    private static final Logger log = LoggerFactory.getLogger(RestUtils.class);
    
    public static void sendResponse(RestResponse restResponse) throws RestException {
        if (restResponse == null) return; // Allowed, when the specific handler prefers to do this
        HttpResponse httpResponse;
        if (restResponse.getBody() == null) {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
            setContentLength(httpResponse, 0);
        } else {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, restResponse.getBody());
            httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, restResponse.getContentType());
            setContentLength(httpResponse, restResponse.getBody().readableBytes());
        }

        RestRequest restRequest = restResponse.getRestRequest();
        ChannelFuture writeFuture = restRequest.getChannelHandlerContext().writeAndFlush(httpResponse);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(restRequest.getHttpRequest())) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    /**
     * Sends base HTTP response indicating that we'll use chunked transfer encoding
     */
    public static void startChunkedTransfer(RestRequest req, String contentType) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        response.headers().set(Names.CONTENT_TYPE, contentType);
        
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        ChannelFuture writeFuture = ctx.write(response);
        writeFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
    
    /**
     * Writes the provided set of messages in a delimited format. For JSON this means
     * just concat-ing them. For Protobuf, every message is prepended with a byte size. 
     */
    public static <T extends MessageLite> void writeChunk(RestRequest req, String contentType, List<T> messages, Schema<T> schema) throws IOException {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();

        // Write a chunk containing a delimited message
        ByteBuf buf = ctx.alloc().buffer();
        ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);

        if (BINARY_MIME_TYPE.equals(contentType)) {
            for (T message : messages) {
                message.writeDelimitedTo(channelOut);
            }
        } else {
            JsonGenerator generator = req.createJsonGenerator(channelOut);
            for (T message : messages) {
                JsonIOUtil.writeTo(generator, message, schema, false);
            }
            generator.close();
        }
        channelOut.flush();

        Channel ch = ctx.channel();
        ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultHttpContent(buf));
        try {
            while (!ch.isWritable() && ch.isOpen()) {
                writeFuture.await(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for channel to become writable", e);
            // TODO return? throw up?
        }
    }
    
    /**
     * Send empty chunk downstream to signal end of response
     */
    public static void stopChunkedTransfer(RestRequest req) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        ChannelFuture chunkWriteFuture = ctx.writeAndFlush(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
        chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * Searches for a valid parameter name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<Parameter> matchParameterName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                Parameter p = mdb.getParameter(id);
                if (p != null) {
                    return new MatchResult<>(p, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Searches for a valid container name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<SequenceContainer> matchContainerName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                SequenceContainer c = mdb.getSequenceContainer(id);
                if (c != null) {
                    return new MatchResult<>(c, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Searches for a valid command name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<MetaCommand> matchCommandName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                MetaCommand c = mdb.getMetaCommand(id);
                if (c != null) {
                    return new MatchResult<>(c, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Greedily matches a namespace
     */
    public static MatchResult<String> matchXtceDbNamespace(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        String matchedNamespace = null;
        
        String segment = req.getPathSegment(pathOffset);
        if (mdb.containsNamespace(segment)) {
            matchedNamespace = segment;
        } else if (mdb.containsNamespace("/" + segment)) {
            matchedNamespace = "/" + segment; 
        } else if ("yamcs".equals(segment)) {
            matchedNamespace = segment;
        }
        
        int beyond = pathOffset;
        if (matchedNamespace != null) {
            beyond++;
            if (matchedNamespace.startsWith("/")) {
                for (int i = pathOffset+1; i < req.getPathSegmentCount(); i++) {
                    String potential = matchedNamespace + "/" + req.getPathSegment(i);
                    if (mdb.containsNamespace(potential)) {
                        matchedNamespace = potential;
                        beyond++;
                    }
                }
            }
        }
        
        if (matchedNamespace != null) {
            return new MatchResult<>(matchedNamespace, beyond);
        } else {
            return new MatchResult<>(null, -1);
        }
    }
    
    public static class MatchResult <T> {
        private final NamedObjectId requestedId;
        private final T match;
        private final int pathOffset; // positioned after the match
        
        MatchResult(T match, int pathOffset, NamedObjectId requestedId) {
            this.match = match;
            this.pathOffset = pathOffset;
            this.requestedId = requestedId;
        }
        
        MatchResult(T match, int pathOffset) {
            this.match = match;
            this.pathOffset = pathOffset;
            requestedId = null;
        }
        
        public boolean matches() {
            return match != null;
        }
        
        public NamedObjectId getRequestedId() {
            return requestedId;
        }
        
        public T getMatch() {
            return match;
        }
        
        public int getPathOffset() {
            return pathOffset;
        }
    }
}
