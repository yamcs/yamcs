package org.yamcs.http;

import org.yamcs.security.User;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;

/**
 * A {@link HttpHandler} that allows request bodies.
 */
public abstract class BodyHandler extends HttpHandler {

    @Override
    public void doHandle(ChannelHandlerContext ctx, HttpRequest msg, User user) {
        ctx.pipeline().addLast(new HttpContentCompressor());
        ctx.pipeline().addLast(new HttpObjectAggregator(65536));
        ctx.pipeline().addLast(new NettyBodyHandler(user));
        ctx.fireChannelRead(msg);
    }

    private class NettyBodyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private User user;

        public NettyBodyHandler(User user) {
            this.user = user;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            BodyHandler.super.doHandle(ctx, msg, user);
        }
    }
}
