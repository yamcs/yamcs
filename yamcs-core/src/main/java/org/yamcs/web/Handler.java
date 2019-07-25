package org.yamcs.web;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public abstract class Handler extends SimpleChannelInboundHandler<FullHttpRequest> {

    abstract void handle(ChannelHandlerContext ctx, FullHttpRequest req);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        handle(ctx, msg);
    }
}
