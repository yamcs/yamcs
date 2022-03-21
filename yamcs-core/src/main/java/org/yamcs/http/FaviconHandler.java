package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Handlers favicon requests. Some of these are automatically issued by browsers, others are referenced in header
 * section of both the auth app and yamcs-web.
 */
@Sharable
public class FaviconHandler extends Handler {

    public static final String[] HANDLED_PATHS = new String[] {
            "apple-touch-icon-precomposed.png",
            "apple-touch-icon.png",
            "favicon.ico",
            "favicon-16x16.png",
            "favicon-32x32.png",
            "safari-pinned-tab.svg",
    };

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();

        String resource = ctx.getPathWithoutContext();

        ByteBuf body = ctx.createByteBuf();
        try (InputStream in = getClass().getResourceAsStream("/favicon" + resource)) {
            if (in == null) { // No apple-touch-icon-precomposed.png, but should still respond 404.
                throw new NotFoundException();
            }
            try (ByteBufOutputStream out = new ByteBufOutputStream(body)) {
                ByteStreams.copy(in, out);
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        if (resource.endsWith(".ico")) {
            response.headers().set(CONTENT_TYPE, "image/x-icon");
        } else if (resource.endsWith(".svg")) {
            response.headers().set(CONTENT_TYPE, "image/svg+xml");
        } else if (resource.endsWith(".png")) {
            response.headers().set(CONTENT_TYPE, "image/png");
        }
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        ctx.sendResponse(response);
    }
}
