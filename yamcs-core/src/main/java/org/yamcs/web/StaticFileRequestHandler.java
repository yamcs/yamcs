package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.activation.MimetypesFileTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;


public class StaticFileRequestHandler extends AbstractRequestHandler {
    public static String WEB_Root;
    static MimetypesFileTypeMap mimeTypesMap;
    public static final int HTTP_CACHE_SECONDS = 60;
    private static boolean zeroCopyEnabled = true;
    
    final static Logger log=LoggerFactory.getLogger(StaticFileRequestHandler.class.getName());
    
    public static void init() throws ConfigurationException {
    	if(mimeTypesMap!=null) return;
    	
    	InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mime.types");
    	if(is==null) {
    		throw new ConfigurationException("Cannot find the mime.types file in the classpath");
    	}
    	mimeTypesMap=new MimetypesFileTypeMap(is);
        YConfiguration yconfig = YConfiguration.getConfiguration("yamcs");
    	WEB_Root=yconfig.getString("webRoot");
    	if (yconfig.containsKey("zeroCopyEnabled")) {
            zeroCopyEnabled = yconfig.getBoolean("zeroCopyEnabled");
        }
    }
    
    void handleStaticFileRequest(ChannelHandlerContext ctx, HttpRequest req, String path) throws Exception {
        log.debug("handling static file request for {}", path);
        path = sanitizePath(path);
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        final File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            log.warn("{} does not exist or is hidden", file.toString());
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = req.headers().get(HttpHeaders.Names.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client does not have milliseconds 
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }
        
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignore) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpHeaders.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpHeaders.isKeepAlive(req)) {
            response.headers().set(Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        System.out.println("sending response: "+response);
        // Write the initial line and the header.
        ctx.write(response);

        // Write the content.
        ChannelFuture sendFileFuture;
        ChannelFuture lastContentFuture;
        if (zeroCopyEnabled && ctx.pipeline().get(SslHandler.class) == null) {
            sendFileFuture = ctx.writeAndFlush(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {           
            //to be replaced with HttpChunkedInput when netty is upgraded.
            sendFileFuture = ctx.writeAndFlush(new ChunkedFile(raf, 0, fileLength, 8192),  ctx.newProgressivePromise());
            // the HttpChunkedInput will take care of the end marker so the next line can be replaced with this one:
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
            //lastContentFuture = sendFileFuture;
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        }

        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                if (total < 0) { // total unknown
                    log.trace(future.channel() + " Transfer progress: " + progress);
                } else {
                    log.trace(future.channel() + " Transfer progress: " + progress + " / " + total);
                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                log.debug(future.channel() + " Transfer complete: " +file);
            }
        });

        // Decide whether to close the connection or not.
        if (!HttpHeaders.isKeepAlive(req)) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
        
    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        response.headers().set(Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
    
    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(Names.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(Names.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(Names.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    
    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     * 
     * @param ctx
     *            Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
        
    private String sanitizePath(String path) {
        path = path.replace('/', File.separatorChar);

        int qsIndex = path.indexOf('?');
        if (qsIndex != -1) {
            path = path.substring(0, qsIndex);
        }

        if (path.contains(File.separator + ".") ||
            path.contains("." + File.separator) ||
            path.startsWith(".") || path.endsWith(".")) {
            return null;
        }

        // Convert to absolute path.
        return WEB_Root + File.separator + path;
    }
}
