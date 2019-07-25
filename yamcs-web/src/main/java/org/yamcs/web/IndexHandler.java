package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import org.yamcs.api.MediaType;
import org.yamcs.utils.TemplateProcessor;

import com.google.common.collect.ImmutableMap;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Handler that always responds with the contents of the index.html file of the webapp. The file is generated
 * dynamically because we do some templating on it.
 */
@Sharable
public class IndexHandler extends Handler {

    private String contextPath;
    private Path indexFile;

    private String html;
    private FileTime cacheTime;

    public IndexHandler(HttpServer httpServer, Path webRoot) {
        contextPath = httpServer.getContextPath();
        indexFile = webRoot.resolve("index.html");
    }

    @Override
    void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.GET) {
            if (!Files.exists(indexFile)) {
                HttpRequestHandler.sendPlainTextError(ctx, req, NOT_FOUND);
                return;
            }

            try {
                FileTime lastModified = Files.getLastModifiedTime(indexFile);
                if (!lastModified.equals(cacheTime)) {
                    String template = new String(Files.readAllBytes(indexFile), StandardCharsets.UTF_8);
                    Map<String, String> args = ImmutableMap.of("contextPath", contextPath);
                    html = TemplateProcessor.process(template, args);
                    cacheTime = lastModified;
                }
            } catch (IOException e) {
                HttpRequestHandler.sendPlainTextError(ctx, req, INTERNAL_SERVER_ERROR);
                return;
            }

            ByteBuf body = ctx.alloc().buffer();
            body.writeCharSequence(html, StandardCharsets.UTF_8);

            HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, body);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaType.HTML);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());

            // Recommend clients to not cache this file. We hash all of our
            // web files, and this reduces likelihood of attempting to load
            // the app from an outdated index.html.
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, must-revalidate");

            HttpRequestHandler.sendResponse(ctx, req, response, true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }
}
