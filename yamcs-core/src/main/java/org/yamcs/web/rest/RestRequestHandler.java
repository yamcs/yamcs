package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.yamcs.web.rest.RestUtils.sendResponse;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.web.AbstractRequestHandler;
import org.yamcs.web.rest.archive.ArchiveRequestHandler;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.protostuff.JsonIOUtil;

/**
 * Defines the basic contract of what a REST handler should abide to.
 */
public abstract class RestRequestHandler extends AbstractRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(RestRequestHandler.class);
    
    public void handleRequestOrError(RestRequest req, int handlerOffset) {
        try {
            sendResponse(handleRequest(req, handlerOffset));
        } catch (InternalServerErrorException e) {
            log.error("Reporting internal server error to rest client", e);
            sendError(req, e.getHttpResponseStatus(), e);
        } catch (RestException e) {
            log.warn("Sending nominal exception back to rest client", e);
            sendError(req, e.getHttpResponseStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error " + e, e);
            sendError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
    
    private void sendError(RestRequest ctx, HttpResponseStatus status) {
        sendError(ctx.getChannelHandlerContext(), status);
    }

    /**
     * Used for sending back generic exceptions. Clients conventionally should deserialize to this
     * when the status is not 200.
     */
    private void sendError(RestRequest req, HttpResponseStatus status, Throwable t) {
        String contentType = req.deriveTargetContentType();
        if (JSON_MIME_TYPE.equals(contentType)) {
            try {
                ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                JsonGenerator generator = req.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, toException(t).build(), SchemaWeb.RestExceptionMessage.WRITE, false);
                generator.close();
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, JSON_MIME_TYPE); // UTF-8 by default IETF RFC4627
                setContentLength(response, buf.readableBytes());
                // Close the connection as soon as the error message is sent.
                req.getChannelHandlerContext().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } catch (IOException e2) {
                log.error("Could not create Json Generator", e2);
                log.debug("Original exception not sent to client", t);
                sendError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR); // text/plain
            }
        } else if (PROTOBUF_MIME_TYPE.equals(contentType)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
            try {
                toException(t).build().writeTo(channelOut);
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, PROTOBUF_MIME_TYPE);
                setContentLength(response, buf.readableBytes());
                req.getChannelHandlerContext().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } catch (IOException e2) {
                log.error("Could not write to channel buffer", e2);
                log.debug("Original exception not sent to client", t);
                sendError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR); // text/plain
            }
        } else {
            sendError(req, status); // text/plain
        }
    }

    /**
     * Wraps all the logic that deals with a RestRequest. Requests should always
     * return something, which is why a return type is enforced. For handlers
     * that have to stream their response, use <tt>return null;</tt> to
     * explicitly turn off a forced response. See {@link ArchiveRequestHandler}
     * for an example of this.
     * 
     * @param pathOffset
     *            the path offset wherein this handler operates. Use this to
     *            correctly index into {@link RestRequest#getPathSegment(int)}
     */
    public abstract RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException;
    
    /**
     * Helper method to throw a BadRequestException on incorrect requests. This is some validation mechanism
     * beyond proto, where we try to keep things optional
     */
    protected <T> T required(T object, String message) throws BadRequestException {
        if(object != null)
            return object;
        else
            throw new BadRequestException(message);
    }
    
    /**
     * Just a little shortcut because builders are dead ugly
     */
    private static RestExceptionMessage.Builder toException(Throwable t) {
        RestExceptionMessage.Builder exceptionb = RestExceptionMessage.newBuilder();
        exceptionb.setType(t.getClass().getSimpleName());
        if (t.getMessage() != null) {
            exceptionb.setMsg(t.getMessage());
        }
        return exceptionb;
    }
}
