package com.spaceapplications.yamcs.scpi;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.write("hello world");
        ctx.flush();
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
	}

}