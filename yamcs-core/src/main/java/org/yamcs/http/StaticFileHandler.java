package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;

public class StaticFileHandler extends HttpHandler {

    public static final int HTTP_CACHE_SECONDS = 60;
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

    protected String route;
    protected List<Path> staticRoots;
    private boolean zeroCopyEnabled = true;

    public StaticFileHandler(String route, Path staticRoot) {
        this(route, Arrays.asList(staticRoot));
    }

    public StaticFileHandler(String route, List<Path> staticRoots) {
        this.route = Objects.requireNonNull(route);
        this.staticRoots = staticRoots;
    }

    @Override
    public boolean requireAuth() {
        return false;
    }

    public void setZeroCopyEnabled(boolean zeroCopyEnabled) {
        this.zeroCopyEnabled = zeroCopyEnabled;
    }

    public void setStaticRoots(List<Path> staticRoots) {
        this.staticRoots = staticRoots;
    }

    @Override
    public void handle(HandlerContext ctx) {
        // Avoid warnings for *.map requests with browser devtools
        if (ctx.getNettyHttpRequest().method() == HttpMethod.OPTIONS) {
            ctx.sendAllow(HttpMethod.GET);
            return;
        }

        ctx.requireGET();
        var filePath = getFilePath(ctx);
        handleStaticFileRequest(ctx.getNettyChannelHandlerContext(),
                ctx.getNettyHttpRequest(), filePath);
    }

    protected String getFilePath(HandlerContext ctx) {
        var uri = ctx.getPathWithoutContext();

        // Chop off a prefix such as /static/
        return uri.substring(route.length() + 1);
    }

    protected File locateFile(String path) {
        for (var staticRoot : staticRoots) { // Stop on first match
            var file = staticRoot.resolve(path).toFile();
            if (!file.isHidden() && file.exists()) {
                return file;
            }
        }
        return null;
    }

    private void handleStaticFileRequest(ChannelHandlerContext ctx, HttpRequest req, String rawPath) {
        log.debug("Handling static file request for {}", rawPath);
        String path = sanitizePath(rawPath);
        if (path == null) {
            HttpRequestHandler.sendPlainTextError(ctx, req, FORBIDDEN);
            return;
        }

        File file = locateFile(path);

        if (file == null) {
            log.warn("File {} does not exist or is hidden. Searched under {}", path, staticRoots);
            HttpRequestHandler.sendPlainTextError(ctx, req, NOT_FOUND);
            return;
        }
        if (!file.isFile()) {
            HttpRequestHandler.sendPlainTextError(ctx, req, FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = req.headers().get(IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
            Date ifModifiedSinceDate;
            try {
                ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
                // Only compare up to the second because the datetime format we send to the client does not have
                // milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = file.lastModified() / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    sendNotModified(ctx, req);
                    return;
                }
            } catch (ParseException e) {
                log.debug("Cannot parse {} header'{}'", IF_MODIFIED_SINCE, ifModifiedSince);
            }
        }

        boolean zeroCopy = zeroCopyEnabled && ctx.pipeline().get(SslHandler.class) == null;

        long fileLength = file.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);

        if (HttpUtil.isKeepAlive(req)) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
        } else {
            response.headers().set(CONNECTION, CLOSE);
        }

        if (zeroCopy) {
            HttpUtil.setContentLength(response, fileLength);
        } else {
            // chunked HTTP is required for compression to work because we don't know the size of the compressed file.
            HttpUtil.setTransferEncodingChunked(response, true);
            ctx.pipeline().addLast(new HttpContentCompressor());
            // Note that the ChunkedWriteHandler here will just read the file chunk by chunk.
            // The real HTTP chunk encoding is performed by the HttpServerCodec/HttpContentEncoder which sits first in
            // the pipeline
            ctx.pipeline().addLast(new ChunkedWriteHandler());
            // propagate the request to the new handlers in the pipeline that need to configure themselves
            ctx.fireChannelRead(req);
        }

        // Write the initial line and the header.
        ctx.channel().writeAndFlush(response);

        // Write the content.
        ChannelFuture sendFileFuture;
        ChannelFuture lastContentFuture;
        if (zeroCopy) {
            sendFileFuture = ctx.writeAndFlush(new DefaultFileRegion(file, 0, fileLength), ctx.newProgressivePromise());
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            try {
                var chunkedFile = new ChunkedFile(file, 8192);
                sendFileFuture = ctx.channel().writeAndFlush(new HttpChunkedInput(chunkedFile),
                        ctx.newProgressivePromise());
                lastContentFuture = sendFileFuture;
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        }

        final File finalFile = file;
        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                if (log.isTraceEnabled()) {
                    if (total < 0) { // total unknown
                        log.trace(future.channel() + " Transfer progress: " + progress);
                    } else {
                        log.trace(future.channel() + " Transfer progress: " + progress + " / " + total);
                    }
                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                if (log.isDebugEnabled()) {
                    log.debug(future.channel() + " Transfer complete: " + finalFile);
                }
            }
        });

        log.debug("{} {} 200", req.method(), req.uri());
        if (!HttpUtil.isKeepAlive(req)) {
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param file
     *            file to extract content type
     */
    protected void setContentTypeHeader(HttpResponse response, File file) {
        response.headers().set(CONTENT_TYPE, MIME.getMimetype(file));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param fileToCache
     *            file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(LAST_MODIFIED,
                dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     */
    private void sendNotModified(ChannelHandlerContext ctx, HttpRequest req) {
        log.debug("{} {} 304", req.method(), req.uri());
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        response.headers().set(CONTENT_LENGTH, 0);
        setDateHeader(response);

        if (HttpUtil.isKeepAlive(req)) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
            ctx.channel().writeAndFlush(response);
        } else {
            response.headers().set(CONNECTION, CLOSE);
            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String sanitizePath(String path) {
        path = path.replace('/', File.separatorChar);
        if (path.contains(File.separator + ".") ||
                path.contains("." + File.separator) ||
                path.startsWith(".") || path.endsWith(".")) {
            return null;
        }
        return path;
    }

    /**
     * Sets the Date header for the HTTP response
     */
    protected static void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }
}
