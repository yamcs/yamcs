package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.yamcs.web.rest.RestUtils.sendResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.web.AbstractRequestHandler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.protostuff.JsonIOUtil;

/**
 * Routes any REST API calls to their final request handler.
 */
public class ApiRouter extends AbstractRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiRouter.class);
    
    // This can be static, because the whole request-handling operates on a single thread
    private static JsonFactory jsonFactory = new JsonFactory();
    
    private List<RestRequestHandler> requestHandlers = new ArrayList<>();
    
    public ApiRouter() {
        requestHandlers.add(new ArchiveRequestHandler());
        requestHandlers.add(new MdbRequestHandler());
        requestHandlers.add(new CommandingRequestHandler());
        requestHandlers.add(new ParameterRequestHandler());
        requestHandlers.add(new AlarmsRequestHandler());
        requestHandlers.add(new EventsRequestHandler());
        requestHandlers.add(new ManagementRequestHandler());
        requestHandlers.add(new ProcessorRequestHandler());
        requestHandlers.add(new AuthorizationRequestHandler());
        requestHandlers.add(new SimulationTimeService.SimTimeRequestHandler());
    }
    
    /**
     * Handles any REST-request
     * 
     * @param yamcsInstance
     *            already parsed from the path by the HttpSocketServerHandler
     * @param remainingUri
     *            the remaining path without the <tt>/(instance)</tt> bit
     */
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest, String yamcsInstance, String remainingUri, AuthenticationToken authToken) {
        if (remainingUri == null) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        
        String[] path = remainingUri.split("/", 2);
        if (path.length == 0) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        
        RestRequest req = new RestRequest(ctx, httpRequest, yamcsInstance, authToken, jsonFactory);
        int handlerOffset = 2; // Relative to 'full' original path. 0 -> '', 1 -> instance, 2 -> handler 
        try {
            String requestPath = req.getPathSegment(handlerOffset);
            for (RestRequestHandler handler : requestHandlers) {
                if (handler.getPath().equals(requestPath)) {
                    sendResponse(handler.handleRequest(req, handlerOffset + 1));
                    return;
                }
            }
            log.warn("Unknown request received: '{}'", req.getPathSegment(handlerOffset));
            throw new NotFoundException(req);
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
        } else if (BINARY_MIME_TYPE.equals(contentType)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
            try {
                toException(t).build().writeTo(channelOut);
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, BINARY_MIME_TYPE);
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
