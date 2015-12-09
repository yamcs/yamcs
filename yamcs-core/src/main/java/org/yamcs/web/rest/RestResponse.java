package org.yamcs.web.rest;

import java.io.IOException;

import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

public class RestResponse {
    
    private MediaType contentType;
    private RestRequest restRequest;
    private ByteBuf body;
    
    /**
     * A response, without a Content-Type header
     */
    RestResponse(RestRequest restRequest) {
        this.restRequest = restRequest;
    }
    
    /**
     * A response of the specified contentType, passing in any ByteBuf.
     * This is a catch-all constructor that enables response that are not JSON or GPB.
     */
    RestResponse(RestRequest restRequest, MediaType contentType, ByteBuf body) {
        this.restRequest = restRequest;
        this.contentType = contentType;
        this.body = body;
    }
    
    /**
     * A response where the specified message is serialized in JSON
     * or Protobuf format to the request body.
     */
    <T extends MessageLite> RestResponse(RestRequest restRequest, T responseMsg, Schema<T> responseSchema) throws HttpException {
        this.restRequest = restRequest;
        body = restRequest.getChannelHandlerContext().alloc().buffer();
        ByteBufOutputStream channelOut = new ByteBufOutputStream(body);
        try {
            if (MediaType.PROTOBUF.equals(getContentType())) {
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
    
    public MediaType getContentType() {
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
