package org.yamcs.web.rest;

import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;
import com.google.protobuf.MessageLite;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders.Names;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.RestExceptionMessage;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.AbstractRequestHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Collects some typical patterns which might be of use for extending classes when dealing with incoming
 * REST requests.
 *
 * <p>This is currently in no way enforced to extending classes since we would need to do a bit
 * more work as far as integrating the different approaches (archiverequesthandler does some special stuff
 * to support retrieving large dumps of unknown size).
 *
 * <p>Provides extra helper logic to return exceptions in the preferred outbound media type.
 */
public abstract class AbstractRestRequestHandler extends AbstractRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestRequestHandler.class);
    private static final String[] DEFAULT_INBOUND_MEDIA_TYPES = new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    private static final String[] DEFAULT_OUTBOUND_MEDIA_TYPES = new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    private JsonFactory jsonFactory = new JsonFactory();

    public abstract void handleRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest, String yamcsInstance, String remainingUri) throws RestException;

    /**
     * Accepted Content-Type headers (in priority order, defaults to first if unspecified).
     * <p>By default support for JSON and GPB is configured.
     */
    public String[] getSupportedInboundMediaTypes() {
        return DEFAULT_INBOUND_MEDIA_TYPES;
    }

    /**
     * Supported Accept headers (if unspecified in request, will be matched
     * to inbound meda type or first in this list)
     * <p>By default support for JSON and GPB is configured
     */
    public String[] getSupportedOutboundMediaTypes() {
        return DEFAULT_OUTBOUND_MEDIA_TYPES;
    }

    /**
     * Derives the content type of the incoming request. Supported media types
     * are treated in priority order if no content type was specified.
     */
    String getSourceContentType(HttpRequest httpRequest) {
        if (httpRequest.headers().contains(Names.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(Names.CONTENT_TYPE);
            for (String supportedContentType : getSupportedInboundMediaTypes()) {
                if (supportedContentType.equals(declaredContentType)) {
                    return declaredContentType;
                }
            }
        }

        // Assume default for simplicity
        return getSupportedInboundMediaTypes()[0];
    }

    /**
     * Derives an applicable content type for the output.
     *
     * This tries to match supported media types with the ACCEPT header,
     * else it will revert to the (derived) source content type.
     */
    String getTargetContentType(HttpRequest httpRequest) {
        if (httpRequest.headers().contains(Names.ACCEPT)) {
            String acceptedContentType = httpRequest.headers().get(Names.ACCEPT);
            for (String supportedContentType : getSupportedOutboundMediaTypes()) {
                if (supportedContentType.equals(acceptedContentType)) {
                    return acceptedContentType;
                }
            }
        } else {
            String sourceContentType = getSourceContentType(httpRequest);
            for (String supportedContentType : getSupportedOutboundMediaTypes()) {
                if (supportedContentType.equals(sourceContentType)) {
                    return supportedContentType;
                }
            }
        }

        // Unsupported content type or wildcard, like */*, just use default
        return getSupportedOutboundMediaTypes()[0];
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about
     * what the HTTP method is. Any required checks should be done in extending classes.
     */
    protected <T extends MessageLite.Builder> T readMessage(FullHttpRequest httpRequest, Schema<T> sourceSchema) throws BadRequestException {
        String sourceContentType = getSourceContentType(httpRequest);
        InputStream cin = new ByteBufInputStream(httpRequest.content());
        T msg = sourceSchema.newMessage();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpHeaders.getContentLength(httpRequest) > 0) {
            try {
                if (BINARY_MIME_TYPE.equals(sourceContentType)) {
                    msg.mergeFrom(cin);
                } else {
                    JsonIOUtil.mergeFrom(cin, msg, sourceSchema, false);
                }
            } catch(IOException e) {
                throw new BadRequestException(e);
            } finally {
                // GPB's mergeFrom does not close the stream, not sure about JsonIOUtil
                try { cin.close(); } catch (IOException e) {}
            }
        }
        return msg;
    }

    /**
     * Writes back a response in one of the supported media types
     */
    protected <T extends MessageLite> void writeMessage(ChannelHandlerContext ctx, FullHttpRequest httpRequest, QueryStringDecoder qsDecoder, T responseMsg, Schema<T> responseSchema) throws RestException {
        String targetContentType = getTargetContentType(httpRequest);
        ByteBuf buf = Unpooled.buffer();
        ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
        try {
            if (BINARY_MIME_TYPE.equals(targetContentType)) {
                responseMsg.writeTo(channelOut);
            } else {
                JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
                generator.close();
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
        setContentTypeHeader(httpResponse, targetContentType);

        ChannelFuture writeFuture = ctx.write(httpResponse);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(httpRequest)) {
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

    protected JsonGenerator createJsonGenerator(OutputStream out, QueryStringDecoder qsDecoder) throws IOException {
        JsonGenerator generator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);
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
     * Used for sending back generic exceptions. Clients conventionally should deserialize to this
     * when the status is not 200.
     */
    protected void sendError(Throwable t, FullHttpRequest req, QueryStringDecoder qsDecoder, ChannelHandlerContext ctx, HttpResponseStatus status) {
        
        String contentType = getTargetContentType(req);
        if (JSON_MIME_TYPE.equals(contentType)) {
            try {
        	ByteBuf buf = Unpooled.buffer();
        	ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                JsonIOUtil.writeTo(generator, toException(t).build(), SchemaRest.RestExceptionMessage.WRITE, false);
                generator.close();
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, JSON_MIME_TYPE); // UTF-8 by default IETF RFC4627
                // Close the connection as soon as the error message is sent.
                ctx.channel().write(response).addListener(ChannelFutureListener.CLOSE);
            } catch (IOException e2) {
                log.error("Could not create Json Generator", e2);
                log.debug("Original exception not sent to client", t);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR); // text/plain
            }
        } else if (BINARY_MIME_TYPE.equals(contentType)) {
            ByteBuf buf = Unpooled.buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
            try {
                toException(t).build().writeTo(channelOut);
                HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
                setContentTypeHeader(response, BINARY_MIME_TYPE);
                setContentLength(response, buf.readableBytes());
            } catch (IOException e2) {
                log.error("Could not write to channel buffer", e2);
                log.debug("Original exception not sent to client", t);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR); // text/plain
            }
        } else {
            sendError(ctx, status); // text/plain
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
