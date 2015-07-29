package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.RestExceptionMessage;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.AbstractRequestHandler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.protostuff.JsonIOUtil;

/**
 * Handles everything under /api. In the future could also be used to handle multiple versions,
 * if ever needed. (e.g. /api/v2).
 */
public class ApiRequestHandler extends AbstractRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestHandler.class);
    public static final String ARCHIVE_PATH = "archive";
    public static final String MDB_PATH = "mdb";
    public static final String COMMANDING_PATH = "commanding";
    public static final String PARAMETER_PATH = "parameter";
    public static final String MANAGEMENT_PATH = "management";
    public static final String PROCESSOR_PATH = "processor";

    static ArchiveRequestHandler archiveRequestHandler=new ArchiveRequestHandler();
    static MdbRequestHandler mdbRequestHandler=new MdbRequestHandler();
    static CommandingRequestHandler commandingRequestHandler=new CommandingRequestHandler();
    static ParameterRequestHandler parameterRequestHandler=new ParameterRequestHandler();
    static ManagementRequestHandler managementRequestHandler=new ManagementRequestHandler();
    static ProcessorRequestHandler processorRequestHandler=new ProcessorRequestHandler();
    
    // This can be static, because the whole request-handling operates on a single thread
    private static JsonFactory jsonFactory = new JsonFactory();
    
    /**
     * Handles any REST-request
     * 
     * @param yamcsInstance
     *            already parsed from the path by the HttpSocketServerHandler
     * @param remainingUri
     *            the remaining path without the <tt>/(instance)/api</tt> bit
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
        int handlerOffset = 3; // Relative to 'full' original path. 0-> '', 1 -> instance, 2 -> 'api', 3 -> handler 
        try {
            switch (req.getPathSegment(handlerOffset)) {
            case ARCHIVE_PATH:
                sendResponse(archiveRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            case MDB_PATH:
                sendResponse(mdbRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            case COMMANDING_PATH:
                sendResponse(commandingRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            case PARAMETER_PATH:
                sendResponse(parameterRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            case MANAGEMENT_PATH:
                sendResponse(managementRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            case PROCESSOR_PATH:
                sendResponse(processorRequestHandler.handleRequest(req, handlerOffset + 1));
                break;
            default:
                log.warn("Unknown request received: '{}'", req.getPathSegment(handlerOffset));
                throw new NotFoundException(req);
            }
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
    
    private void sendResponse(RestResponse restResponse) throws RestException {
        HttpResponse httpResponse;
        if (restResponse.getBody() == null) {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
            setContentLength(httpResponse, 0);
        } else {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, restResponse.getBody());
            setContentTypeHeader(httpResponse, restResponse.getContentType());
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
                JsonIOUtil.writeTo(generator, toException(t).build(), SchemaRest.RestExceptionMessage.WRITE, false);
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
        return RestExceptionMessage.newBuilder().setType(t.getClass().getSimpleName()).setMsg(t.getMessage());
    }
}
