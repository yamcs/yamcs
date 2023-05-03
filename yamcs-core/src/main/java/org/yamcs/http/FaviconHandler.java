package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;

import org.yamcs.utils.Mimetypes;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

/**
 * Handlers favicon requests. Some of these are automatically issued by browsers, others are referenced in header
 * section of both the auth app and yamcs-web.
 */
@Sharable
public class FaviconHandler extends Handler {

    private static final Mimetypes MIME = Mimetypes.getInstance();
    public static final String[] HANDLED_PATHS = new String[] {
            "apple-touch-icon-precomposed.png",
            "apple-touch-icon.png",
            "favicon.ico",
            "favicon-16x16.png",
            "favicon-32x32.png",
            "favicon-notification.ico",
            "safari-pinned-tab.svg",
    };

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();

        String resource = ctx.getPathWithoutContext();

        ByteBuf body = ctx.createByteBuf();
        try (var in = getClass().getResourceAsStream("/favicon" + resource)) {
            if (in == null) { // No apple-touch-icon-precomposed.png, but should still respond 404.
                throw new NotFoundException();
            }
            try (var out = new ByteBufOutputStream(body)) {
                ByteStreams.copy(in, out);
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        response.headers().set(CONTENT_TYPE, MIME.getMimetype(resource));
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        ctx.sendResponse(response);
    }
}
