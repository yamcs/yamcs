package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;
import static org.yamcs.web.AbstractRequestHandler.JSON_MIME_TYPE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.yamcs.security.AuthenticationToken;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
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
    private QueryStringDecoder qsDecoder;
    AuthenticationToken authToken;
    private JsonFactory jsonFactory;
    
    private String[] pathSegments;
    
    public RestRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest, String yamcsInstance, AuthenticationToken authToken, JsonFactory jsonFactory) {
        this.channelHandlerContext = channelHandlerContext;
        this.httpRequest = httpRequest;
        this.yamcsInstance = yamcsInstance;
        this.authToken = authToken;
        this.jsonFactory = jsonFactory;
        
        // This is used for some operations that require the Query-String, and also
        // to scan for the 'pretty'-argument, which prettifies any outputted JSON.
        qsDecoder = new QueryStringDecoder(httpRequest.getUri());
        
        // Get splitted path, taking care that URL-encoded slashes are ignored for the split
        pathSegments = httpRequest.getUri().split("/");
        for (int i=0; i<pathSegments.length; i++) {
            pathSegments[i] = QueryStringDecoder.decodeComponent(pathSegments[i]);
        }
    }
    
    /**
     * Returns all decoded path segments. The structure varies from one operation to another
     * but in broad lines amounts to this:
     * <ul>
     *  <li>0. The empty string (because uri's start with a "/")
     *  <li>1. The yamcs instance.
     *  <li>2. The string 'api', to identify anything REST.
     *  <li>3. The general resource, e.g. 'mdb', or 'archive'
     *  <li>4. Optionally, any number of other segments depending on the operation.
     * </ul>
     */
    public String[] getPathSegments() {
        return pathSegments;
    }
    
    /**
     * Returns the decoded URI path segment at the specified index
     */
    public String getPathSegment(int index) {
        return pathSegments[index];
    }
    
    public boolean hasPathSegment(int index) {
        return pathSegments.length > index;
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
    
    public Map<String, List<String>> getQueryParameters() {
        return qsDecoder.parameters();
    }
    
    ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }
    
    HttpRequest getHttpRequest() {
        return httpRequest;
    }
    
    /**
     * Returns a new Json Generator that will have pretty-printing enabled if the original request specified this.
     */
    public JsonGenerator createJsonGenerator(OutputStream out) throws IOException {
        JsonGenerator generator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);
        if (qsDecoder.parameters().containsKey("pretty")) {
            List<String> pretty = qsDecoder.parameters().get("pretty");
            if (pretty != null) {
                String arg = pretty.get(0);
                if (arg == null || "".equals(arg)
                        || "true".equalsIgnoreCase(arg)
                        || "yes".equalsIgnoreCase(arg)) {
                    generator.useDefaultPrettyPrinter();
                }
            }
        }
        return generator;
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
