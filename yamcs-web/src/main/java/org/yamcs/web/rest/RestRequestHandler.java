package org.yamcs.web.rest;

import java.io.IOException;
import java.io.InputStream;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.yamcs.protobuf.Rest.ExceptionMessage;
import org.yamcs.web.AbstractRequestHandler;

import com.dyuproject.protostuff.JsonIOUtil;
import com.dyuproject.protostuff.Schema;
import com.google.protobuf.MessageLite;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Collects some typical patterns which might be of use for extending classes when dealing with incoming
 * REST requests.
 *
 * <p>This is currently in no way forced to be used by extending classes since we would need to do a bit
 * more work as far as integrating the different approaches (archiverequesthandler does some special stuff
 * to support retrieving large dumps of unknown size).
 */
public abstract class RestRequestHandler extends AbstractRequestHandler {

    private JsonFactory jsonFactory = new JsonFactory();

    public abstract void handleRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, MessageEvent evt, String yamcsInstance, String remainingUri) throws Exception;

    /**
     * Derives the content type of the incoming request. Defaults
     * to JSON for improved usability through various client tools.
     */
    protected String getSourceContentType(HttpRequest httpRequest) {
        String sourceContentType = JSON_MIME_TYPE;
        if (httpRequest.containsHeader(Names.CONTENT_TYPE)) {
            String contentType = httpRequest.getHeader(Names.CONTENT_TYPE);
            if (BINARY_MIME_TYPE.equals(contentType)) {
                sourceContentType = BINARY_MIME_TYPE;
            }
        }
        return sourceContentType;
    }

    /**
     * Derives an applicable content type for the output.
     *
     * This tries to match supported media types with the ACCEPT header,
     * else it will revert to the (derived) source content type.
     */
    protected String getTargetContentType(HttpRequest httpRequest) {
        // By default match output content type with input content type
        String targetContentType = getSourceContentType(httpRequest);
        if (httpRequest.containsHeader(Names.ACCEPT)) {
            String accept = httpRequest.getHeader(Names.ACCEPT);
            if (BINARY_MIME_TYPE.equals(accept)) {
                targetContentType = BINARY_MIME_TYPE;
            } else if (JSON_MIME_TYPE.equals(accept)) {
                targetContentType = JSON_MIME_TYPE;
            }
        }
        return targetContentType;
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about
     * what the HTTP method is. Any required checks should be done in extending classes.
     */
    protected <T extends MessageLite.Builder> T readMessage(HttpRequest httpRequest, Schema<T> sourceSchema) throws IOException {
        String sourceContentType = getSourceContentType(httpRequest);
        InputStream cin = new ChannelBufferInputStream(httpRequest.getContent());
        T msg = sourceSchema.newMessage();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpHeaders.getContentLength(httpRequest) > 0) {
            if (BINARY_MIME_TYPE.equals(sourceContentType)) {
                msg.mergeFrom(cin);
            } else {
                JsonIOUtil.mergeFrom(cin, msg, sourceSchema, false);
            }
            cin.close(); // GPB's mergeFrom does not close the stream, not sure about JsonIOUtil
        }
        return msg;
    }

    /**
     * Writes back a response in one of the supported media types
     */
    protected void writeMessage(HttpRequest httpRequest, QueryStringDecoder qsDecoder, MessageEvent evt, MessageLite responseMsg, Schema responseSchema) throws IOException {
        String targetContentType = getTargetContentType(httpRequest);
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream channelOut = new ChannelBufferOutputStream(buf);
        if (BINARY_MIME_TYPE.equals(targetContentType)) {
            responseMsg.writeTo(channelOut);
        } else {
            JsonGenerator generator = jsonFactory.createJsonGenerator(channelOut, JsonEncoding.UTF8);
            if (qsDecoder.getParameters().containsKey("pretty")) {
                generator.useDefaultPrettyPrinter();
            }
            JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
            generator.close();
        }
        HttpResponse httpResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(httpResponse, targetContentType);
        setContentLength(httpResponse, buf.readableBytes());
        httpResponse.setContent(buf);

        Channel ch = evt.getChannel();
        ChannelFuture writeFuture = ch.write(httpResponse);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(httpRequest)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Just a little shortcut because builders are dead ugly
     */
    protected static ExceptionMessage.Builder toException(String type, Throwable t) {
        return ExceptionMessage.newBuilder().setType(type).setMsg(t.getMessage());
    }
}
