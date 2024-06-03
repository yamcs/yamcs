package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * Handles robots.txt requests, to advise against web crawling.
 */
public class RobotsTxtHandler extends HttpHandler {

    public static final String[] HANDLED_PATHS = new String[] { "robots.txt" };

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();

        var body = Unpooled.copiedBuffer("User-agent: *\nDisallow: /\n", StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        response.headers().set(CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=0");
        ctx.sendResponse(response);
    }
}
