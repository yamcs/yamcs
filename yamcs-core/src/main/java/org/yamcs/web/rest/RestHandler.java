package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpHandler;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.RouteHandler;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Defines the basic contract of what a REST handler should abide to.
 */
public abstract class RestHandler extends RouteHandler {
    
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);
    
    public void handleRequestOrError(RestRequest req, int handlerOffset) {
        try {
            // FIXME handleRequest must never return null! Futures are used to follow up on handling
            ChannelFuture responseFuture = handleRequest(req, handlerOffset);
            if (responseFuture == null) return; // Allowed, when the specific handler prefers to do this
            
            /**
             * Follow-up on the successful write, to provide some hints when a future was not actually
             * successfully delivered.
             */
            responseFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        log.error("Error writing out response to client", future.cause());
                        future.channel().close();
                    }
                }
            });

        } catch (InternalServerErrorException e) {
            log.error("Reporting internal server error to client", e);
            sendRestError(req, e.getStatus(), e);
        } catch (HttpException e) {
            log.warn("Sending nominal exception back to client", e);
            sendRestError(req, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error " + e, e);
            sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
    
    protected ChannelFuture sendOK(RestRequest restRequest) {
        ChannelHandlerContext ctx = restRequest.getChannelHandlerContext();
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
        setContentLength(httpResponse, 0);
        return HttpHandler.sendOK(ctx, httpResponse);
    }
    
    protected <T extends MessageLite> ChannelFuture sendOK(RestRequest restRequest, T responseMsg, Schema<T> responseSchema) throws HttpException {
        ByteBuf body = restRequest.getChannelHandlerContext().alloc().buffer();
        ByteBufOutputStream channelOut = new ByteBufOutputStream(body);
        try {
            if (MediaType.PROTOBUF.equals(restRequest.deriveTargetContentType())) {
                responseMsg.writeTo(channelOut);
            } else {
                JsonGenerator generator = restRequest.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
                generator.close();
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        setContentTypeHeader(httpResponse, restRequest.deriveTargetContentType().toString());
        setContentLength(httpResponse, body.readableBytes());
        return HttpHandler.sendOK(restRequest.getChannelHandlerContext(), httpResponse);
    }
    
    protected ChannelFuture sendOK(RestRequest restRequest, MediaType contentType, ByteBuf body) {
        ChannelHandlerContext ctx = restRequest.getChannelHandlerContext();    
        if (body == null) {
            HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
            setContentLength(httpResponse, 0);
            return HttpHandler.sendOK(ctx, httpResponse);
        } else {
            HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
            setContentTypeHeader(httpResponse, contentType.toString());
            setContentLength(httpResponse, body.readableBytes());
            return HttpHandler.sendOK(ctx, httpResponse);
        }
    }
    
    private void sendRestError(RestRequest req, HttpResponseStatus status, Throwable t) {
        MediaType contentType = req.deriveTargetContentType();
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        if (MediaType.JSON.equals(contentType)) {
            try {
                ByteBuf buf = ctx.alloc().buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                JsonGenerator generator = req.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, toException(t).build(), SchemaWeb.RestExceptionMessage.WRITE, false);
                generator.close();
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, MediaType.JSON.toString()); // UTF-8 by default IETF RFC4627
                setContentLength(response, buf.readableBytes());
                HttpHandler.sendError(ctx, response);
            } catch (IOException e2) {
                log.error("Could not create JSON Generator", e2);
                log.debug("Original exception not sent to client", t);
                HttpHandler.sendPlainTextError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else if (MediaType.PROTOBUF.equals(contentType)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
            try {
                toException(t).build().writeTo(channelOut);
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, MediaType.PROTOBUF.toString());
                setContentLength(response, buf.readableBytes());
                HttpHandler.sendError(ctx, response);
            } catch (IOException e2) {
                log.error("Could not write to channel buffer", e2);
                log.debug("Original exception not sent to client", t);
                HttpHandler.sendPlainTextError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            HttpHandler.sendPlainTextError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
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
    public abstract ChannelFuture handleRequest(RestRequest req, int pathOffset) throws HttpException;
    
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
