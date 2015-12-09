package org.yamcs.web;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.rest.RestRequest;

import com.fasterxml.jackson.core.JsonFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;

public class RouteHandler {
    
    // This can be static, because the whole request-handling operates on a single thread
    private static JsonFactory jsonFactory = new JsonFactory();
    
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    
    public static RestRequest toRestRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest, QueryStringDecoder qsDecoder, AuthenticationToken authToken) {
        return new RestRequest(ctx, httpRequest, qsDecoder, authToken, jsonFactory);
    }
    
    /**
     * Sets the Date header for the HTTP response
     *
     * @param response
     *            HTTP response
     */
    protected void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
    
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }
    
    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param type
     *            content type of file to extract
     */
    protected static void setContentTypeHeader(HttpResponse response, String type) {
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, type);
    }
    
    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param lastModified
     *            the time when the file has been last mdified
     */
    protected void setDateAndCacheHeaders(HttpResponse response, Date lastModified) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
    
        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    
        // Add cache headers
        //   time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        //  response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
        // response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(lastModified));
    }
}
