package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.PROTOBUF_MIME_TYPE;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

public class RestResponse {
    
    private String contentType;
    private RestRequest restRequest;
    private ByteBuf body;
    
    /**
     * Makes a new 200 response, without a Content-Type header
     * @param restRequest
     */
    public RestResponse(RestRequest restRequest) {
        this.restRequest = restRequest;
    }
    
    /**
     * Makes a new 200 response of the specified contentType, passing in any ByteBuf.
     * This is a catch-all constructor that enables response that are not JSON or GPB.
     */
    public RestResponse(RestRequest restRequest, String contentType, ByteBuf body) {
        this.restRequest = restRequest;
        this.contentType = contentType;
        this.body = body;
    }
    
    /**
     * Makes a new 200 response, where the specified message is serialized in JSON
     * or Protobuf format to the request body.
     */
    public <T extends MessageLite> RestResponse(RestRequest restRequest, T responseMsg, Schema<T> responseSchema) throws RestException {
        this.restRequest = restRequest;
        body = restRequest.getChannelHandlerContext().alloc().buffer();
        ByteBufOutputStream channelOut = new ByteBufOutputStream(body);
        try {
            if (PROTOBUF_MIME_TYPE.equals(getContentType())) {
                responseMsg.writeTo(channelOut);
            } else {
                JsonGenerator generator = restRequest.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
                generator.close();
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
    }
    
    public String getContentType() {
        if (contentType != null) {
            return contentType;
        } else {
            return restRequest.deriveTargetContentType();
        }
    }
    
    public ByteBuf getBody() {
        return body;
    }
    
    RestRequest getRestRequest() {
        return restRequest;
    }
}
