package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.RestExceptionMessage;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.web.AbstractRequestHandler;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.protostuff.JsonIOUtil;

/**
 * Collects some typical patterns which might be of use for extending classes when dealing with incoming
 * REST requests.
 */
public abstract class AbstractRestRequestHandler extends AbstractRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestRequestHandler.class);
    
    // This can be static, because the whole request-handling operates on a single thread
    private static JsonFactory jsonFactory = new JsonFactory();
    
    protected CsvGenerator csvGenerator = null;

    /**
     * Wraps all the logic that deals with a RestRequest. Requests should always return
     * something, which is why a return type is enforced. For handlers that have to stream
     * their response, use <tt>return null;</tt> to explicitely turn off a forced response.
     */
    public abstract RestResponse handleRequest(RestRequest req) throws RestException;
    
    public void sendResponse(RestResponse restResponse) throws RestException {
        HttpResponse httpResponse;
        if (restResponse.getBody() == null) {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
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

    /**
     * Just a little shortcut because builders are dead ugly
     */
    protected static RestExceptionMessage.Builder toException(Throwable t) {
        return RestExceptionMessage.newBuilder().setType(t.getClass().getSimpleName()).setMsg(t.getMessage());
    }

    protected static JsonGenerator createJsonGenerator(OutputStream out, QueryStringDecoder qsDecoder) throws IOException {
        JsonGenerator generator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);
        if (qsDecoder.parameters().containsKey("pretty")) {
            List<String> pretty = qsDecoder.parameters().get("pretty");
            if (pretty != null) {
                String arg = pretty.get(0);
                if (arg == null || "".equals(arg) || Boolean.parseBoolean(arg)) {
                    generator.useDefaultPrettyPrinter();
                }
            }
        }
        return generator;
    }

    /**
     *  csv generator should be created only once per request since its insert a header in first row
     */
    protected void initCsvGenerator(Yamcs.ReplayRequest replayRequest) {
        csvGenerator = null;
        if(replayRequest.hasParameterRequest()) {
            csvGenerator = new CsvGenerator();
            csvGenerator.initParameterFormatter(replayRequest.getParameterRequest().getNameFilterList());
        }
    }
    
    protected void sendError(RestRequest ctx, HttpResponseStatus status) {
        sendError(ctx.getChannelHandlerContext(), status);
    }

    /**
     * Used for sending back generic exceptions. Clients conventionally should deserialize to this
     * when the status is not 200.
     */
    protected void sendError(RestRequest req, HttpResponseStatus status, Throwable t) {
        String contentType = req.deriveTargetContentType();
        if (JSON_MIME_TYPE.equals(contentType)) {
            try {
                ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                JsonGenerator generator = createJsonGenerator(channelOut, req.qsDecoder);
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
     * Helper method to throw a BadRequestException on incorrect requests. This is some validation mechanism
     * beyond proto, where we try to keep things optional
     */
    protected static <T> T required(T object, String message) throws BadRequestException {
        if(object != null)
            return object;
        else
            throw new BadRequestException(message);
    }
}
