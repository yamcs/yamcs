package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
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
import org.yamcs.utils.Mimetypes;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

/**
 * If configured, handles requests for a custom logo, served from a local file.
 */
@Sharable
public class LogoHandler extends Handler {

    private static final Mimetypes MIME = Mimetypes.getInstance();
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
        response.headers().set(CONTENT_TYPE, MIME.getMimetype(file));
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        ctx.sendResponse(response);
    }
}
