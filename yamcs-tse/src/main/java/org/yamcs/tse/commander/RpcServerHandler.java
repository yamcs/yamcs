package org.yamcs.tse.commander;

import org.yamcs.protobuf.Tse.CommandDeviceRequest;
import org.yamcs.protobuf.Tse.CommandDeviceResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class RpcServerHandler extends SimpleChannelInboundHandler<CommandDeviceRequest> {

    public RpcServerHandler(DevicePool devicePool) {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("RPC client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CommandDeviceRequest request) throws Exception {
        System.out.println("Got a request " + request);
        ctx.write(CommandDeviceResponse.newBuilder().setResponse("im a response"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("RPC client disconnected: " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }
}
