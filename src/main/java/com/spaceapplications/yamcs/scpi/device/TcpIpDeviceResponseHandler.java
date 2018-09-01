package com.spaceapplications.yamcs.scpi.device;

import java.util.concurrent.BlockingQueue;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TcpIpDeviceResponseHandler extends SimpleChannelInboundHandler<String> {

    private TcpIpDevice device;
    private BlockingQueue<String> responseQueue;

    public TcpIpDeviceResponseHandler(TcpIpDevice device, BlockingQueue<String> responseQueue) {
        this.device = device;
        this.responseQueue = responseQueue;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        System.out.println(String.format("[%s] %s", device.id(), msg));
        responseQueue.put(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
