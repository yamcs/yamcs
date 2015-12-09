package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpServerHandler;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.RouteHandler;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.protostuff.JsonIOUtil;

/**
 * Defines the basic contract of what a REST handler should abide to.
 */
public abstract class RestHandler extends RouteHandler {
    
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);
    
    public void handleRequestOrError(RestRequest req, int handlerOffset) {
        try {
            RestResponse restResponse = handleRequest(req, handlerOffset);
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

            ChannelHandlerContext ctx = req.getChannelHandlerContext();    
            HttpServerHandler.sendOK(ctx, req.getHttpRequest(), httpResponse);
        } catch (InternalServerErrorException e) {
            log.error("Reporting internal server error to REST client", e);
            sendRestError(req, e.getStatus(), e);
        } catch (HttpException e) {
            log.warn("Sending nominal exception back to REST client", e);
            sendRestError(req, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error " + e, e);
            sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
    
    private void sendRestError(RestRequest req, HttpResponseStatus status, Throwable t) {
        String contentType = req.deriveTargetContentType();
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        if (JSON_MIME_TYPE.equals(contentType)) {
            try {
                ByteBuf buf = ctx.alloc().buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                JsonGenerator generator = req.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, toException(t).build(), SchemaWeb.RestExceptionMessage.WRITE, false);
                generator.close();
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, JSON_MIME_TYPE); // UTF-8 by default IETF RFC4627
                setContentLength(response, buf.readableBytes());
                HttpServerHandler.sendError(ctx, req.getHttpRequest(), response);
            } catch (IOException e2) {
                log.error("Could not create JSON Generator", e2);
                log.debug("Original exception not sent to client", t);
                HttpServerHandler.sendPlainTextError(ctx, req.getHttpRequest(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else if (PROTOBUF_MIME_TYPE.equals(contentType)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
            try {
                toException(t).build().writeTo(channelOut);
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, PROTOBUF_MIME_TYPE);
                setContentLength(response, buf.readableBytes());
                HttpServerHandler.sendError(ctx, req.getHttpRequest(), response);
            } catch (IOException e2) {
                log.error("Could not write to channel buffer", e2);
                log.debug("Original exception not sent to client", t);
                HttpServerHandler.sendPlainTextError(ctx, req.getHttpRequest(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            HttpServerHandler.sendPlainTextError(ctx, req.getHttpRequest(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Wraps all the logic that deals with a RestRequest. Requests should always
     * return something, which is why a return type is enforced. For handlers
     * that have to stream their response, use <tt>return null;</tt> to
     * explicitly turn off a forced response.
     * 
     * @param pathOffset
     *            the path offset wherein this handler operates. Use this to
     *            correctly index into {@link RestRequest#getPathSegment(int)}
     */
    public abstract RestResponse handleRequest(RestRequest req, int pathOffset) throws HttpException;
    
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
