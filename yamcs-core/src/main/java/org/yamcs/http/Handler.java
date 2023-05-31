package org.yamcs.http;

import org.yamcs.logging.Log;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public abstract class Handler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Log log = new Log(Handler.class);

    public abstract void handle(HandlerContext ctx);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        try {
            String contextPath = ctx.channel().attr(HttpRequestHandler.CTX_CONTEXT_PATH).get();
            handle(new HandlerContext(contextPath, ctx, msg, null));
        } catch (Throwable t) {
            if (!(t instanceof HttpException)) {
                t = new InternalServerErrorException(t);
            }

            HttpException e = (HttpException) t;
            if (e.isServerError()) {
                log.error("Responding '{}': {}", e.getStatus(), e.getMessage(), e);
            } else {
                log.warn("Responding '{}': {}", e.getStatus(), e.getMessage());
            }
            HttpRequestHandler.sendPlainTextError(ctx, msg, e.getStatus());
        }
    }
}
