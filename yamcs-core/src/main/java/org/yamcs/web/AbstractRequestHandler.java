package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.rest.RestRequest;

import com.fasterxml.jackson.core.JsonFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

public class AbstractRequestHandler {
    
    // This can be static, because the whole request-handling operates on a single thread
    private static JsonFactory jsonFactory = new JsonFactory();
    
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    
    public static final String BINARY_MIME_TYPE = "application/octet-stream";
    public static final String CSV_MIME_TYPE = "text/csv";
    public static final String JSON_MIME_TYPE = "application/json";
    public static final String PROTOBUF_MIME_TYPE = "application/protobuf";
    
    public static RestRequest toRestRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest, QueryStringDecoder qsDecoder, AuthenticationToken authToken) {
        return new RestRequest(ctx, httpRequest, qsDecoder, authToken, jsonFactory);
    }
    
    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
    
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
    
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
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
