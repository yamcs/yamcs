package org.yamcs.web;

import org.yamcs.api.MediaType;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;

public class HttpUtils {
    /**
     * Sets the content type header for the HTTP Response
     * @param response 
     * 
     * @param type
     *            content type of file to extract
     */
    public static void setContentTypeHeader(HttpResponse response, MediaType type) {
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, type.toString());
    }
    
}
