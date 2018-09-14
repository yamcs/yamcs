package org.yamcs.tse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Tse.CommandDeviceRequest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TcServerHandler extends SimpleChannelInboundHandler<CommandDeviceRequest> {

    private static final Logger log = LoggerFactory.getLogger(TcServerHandler.class);

    private DeviceManager deviceManager;

    public TcServerHandler(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("TC client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CommandDeviceRequest request) throws Exception {
        Device device = deviceManager.getDevice("simulator" /* TODO */);
        deviceManager.queueCommand(device, request.getMessage());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("TC client disconnected: " + ctx.channel().remoteAddress());
    }
}
