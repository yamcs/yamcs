package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;
import static org.yamcs.web.AbstractRequestHandler.JSON_MIME_TYPE;

import java.io.IOException;
import java.io.InputStream;

import org.yamcs.security.AuthenticationToken;

import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Encapsulates everything to do with one Rest Request. Object is gc-ed, when request ends.
 */
public class RestRequest {
    
    private ChannelHandlerContext channelHandlerContext;
    private FullHttpRequest httpRequest;
    String yamcsInstance;
    @Deprecated
    String remainingUri;
    QueryStringDecoder qsDecoder;
    AuthenticationToken authToken;
    
    public RestRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest, String yamcsInstance, String remainingUri, AuthenticationToken authToken) {
        this.channelHandlerContext = channelHandlerContext;
        this.httpRequest = httpRequest;
        this.yamcsInstance = yamcsInstance;
        this.remainingUri = remainingUri;
        this.authToken = authToken;
        qsDecoder = new QueryStringDecoder(remainingUri);
    }
    
    public String getRemainingUri() {
        return qsDecoder.path();
    }
    
    public boolean isPOST() {
        return httpRequest.getMethod() == HttpMethod.POST;
    }
    
    public void assertPOST() throws MethodNotAllowedException {
        if (!isPOST()) throw new MethodNotAllowedException(this); 
    }
    
    public boolean isGET() {
        return httpRequest.getMethod() == HttpMethod.GET;
    }
    
    public void assertGET() throws MethodNotAllowedException {
        if (!isGET()) throw new MethodNotAllowedException(this); 
    }
    
    public boolean isPUT() {
        return httpRequest.getMethod() == HttpMethod.PUT;
    }
    
    public void assertPUT() throws MethodNotAllowedException {
        if (!isPUT()) throw new MethodNotAllowedException(this); 
    }
    
    public boolean isDELETE() {
        return httpRequest.getMethod() == HttpMethod.DELETE;
    }
    
    public void assertDELETE() throws MethodNotAllowedException {
        if (!isDELETE()) throw new MethodNotAllowedException(this); 
    }
    
    ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }
    
    HttpRequest getHttpRequest() {
        return httpRequest;
    }
    
    /**
     * Deserializes the incoming message extracted from the body. This does not
     * care about what the HTTP method is. Any required checks should be done
     * elsewhere.
     * <p>
     * This method is only able to read JSON or Protobuf, the two auto-supported
     * serialization mechanisms. If a certain operation needs to read anything
     * else, it should check for that itself, and then use
     * {@link #bodyAsInputStream()}.
     */
    public <T extends MessageLite.Builder> T readMessage(Schema<T> sourceSchema) throws BadRequestException {
        String sourceContentType = deriveSourceContentType();
        InputStream cin = bodyAsInputStream();
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
    
    public InputStream bodyAsInputStream() {
        return new ByteBufInputStream(httpRequest.content());
    }
    
    /**
     * Derives the content type of the incoming request. Returns either JSON or
     * BINARY in that order.
     */
    public String deriveSourceContentType() {
        if (httpRequest.headers().contains(Names.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(Names.CONTENT_TYPE);
            if (declaredContentType.equals(JSON_MIME_TYPE)
                    || declaredContentType.equals(BINARY_MIME_TYPE)) {
                return declaredContentType;
            }
        }

        // Assume default for simplicity
        return JSON_MIME_TYPE;
    }

    /**
     * Derives an applicable content type for the output. This tries to match
     * JSON or BINARY media types with the ACCEPT header, else it will revert to
     * the (derived) source content type.
     */
    public String deriveTargetContentType() {
        if (httpRequest.headers().contains(Names.ACCEPT)) {
            String acceptedContentType = httpRequest.headers().get(Names.ACCEPT);
            if (acceptedContentType.equals(JSON_MIME_TYPE)
                    || acceptedContentType.equals(BINARY_MIME_TYPE)) {
                return acceptedContentType;
            }
        }

        // Unsupported content type or wildcard, like */*, just use default
        return deriveSourceContentType();
    }
}
