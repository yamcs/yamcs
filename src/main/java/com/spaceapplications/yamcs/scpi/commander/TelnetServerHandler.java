package com.spaceapplications.yamcs.scpi.commander;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {

    private static final String DEFAULT_PROMPT = "$ ";

    private TelnetCommandHandler commandHandler;

    public TelnetServerHandler(List<Device> devices) {
        commandHandler = new TelnetCommandHandler(devices);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
        ctx.writeAndFlush("Welcome! Run '?' for more info.\n" + DEFAULT_PROMPT);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
        String response = commandHandler.execute(cmd);
        if (response != null) {
            ctx.write(response);
            ctx.write("\n");
        }

        Device device = commandHandler.getConnectedDevice();
        if (device != null) {
            ctx.writeAndFlush(device.getId() + DEFAULT_PROMPT);
        } else {
            ctx.writeAndFlush(DEFAULT_PROMPT);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        commandHandler.disconnectDevice();
        super.channelInactive(ctx);
    }
}
