package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_OCTET_STREAM;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.yamcs.http.Handler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

/**
 * If configured, handles requests for a custom logo, served from a local file.
 */
@Sharable
public class LogoHandler extends Handler {

    private Path file;

    public LogoHandler(Path file) {
        this.file = file;
    }

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();

        var body = ctx.createByteBuf();
        try (var in = Files.newInputStream(file); var out = new ByteBufOutputStream(body)) {
            ByteStreams.copy(in, out);
        } catch (NoSuchFileException e) {
            throw new NotFoundException();
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);

        var filename = file.getFileName().toString().toLowerCase();
        if (filename.endsWith(".apng")) {
            response.headers().set(CONTENT_TYPE, "image/apng");
        } else if (filename.endsWith(".avif")) {
            response.headers().set(CONTENT_TYPE, "image/avif");
        } else if (filename.endsWith(".bmp")) {
            response.headers().set(CONTENT_TYPE, "image/bmp");
        } else if (filename.endsWith(".gif")) {
            response.headers().set(CONTENT_TYPE, "image/gif");
        } else if (filename.endsWith(".ico")) {
            response.headers().set(CONTENT_TYPE, "image/x-icon");
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            response.headers().set(CONTENT_TYPE, "image/jpeg");
        } else if (filename.endsWith(".png")) {
            response.headers().set(CONTENT_TYPE, "image/png");
        } else if (filename.endsWith(".svg")) {
            response.headers().set(CONTENT_TYPE, "image/svg+xml");
        } else if (filename.endsWith(".tif") || filename.endsWith(".tiff")) {
            response.headers().set(CONTENT_TYPE, "image/tiff");
        } else if (filename.endsWith(".webp")) {
            response.headers().set(CONTENT_TYPE, "image/webp");
        } else {
            response.headers().set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        }
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        ctx.sendResponse(response);
    }
}
