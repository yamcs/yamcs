package org.yamcs.web;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

public class RouteHandler {
    
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    
    /**
     * Sets the Date header for the HTTP response
     */
    protected static void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
    
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }
    
    /**
     * Sets the content type header for the HTTP Response
     * 
     * @param type
     *            content type of file to extract
     */
    protected static void setContentTypeHeader(HttpResponse response, String type) {
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, type);
    }
    
    /**
     * Sets the Date and Cache headers for the HTTP Response
     * 
     * @param lastModified
     *            the time when the file has been last mdified
     */
    protected static void setDateAndCacheHeaders(HttpResponse response, Date lastModified) {
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
