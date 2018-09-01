package com.spaceapplications.yamcs.scpi.telnet;

import java.util.List;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.device.Device;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {
    private Commander commander;

    public TelnetServerHandler(Config config, List<Device> devices) {
        commander = new Commander(config, devices);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
        ctx.writeAndFlush("Welcome! Run 'help' for more info.\n" + Commander.DEFAULT_PROMPT);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
        ctx.writeAndFlush(commander.execute(cmd));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        commander.disconnectDevice();
        super.channelInactive(ctx);
    }
}
