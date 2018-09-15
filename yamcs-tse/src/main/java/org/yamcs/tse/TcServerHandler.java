package org.yamcs.tse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Tse.TseCommand;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TcServerHandler extends SimpleChannelInboundHandler<TseCommand> {

    private static final Logger log = LoggerFactory.getLogger(TcServerHandler.class);

    private TcServer tcServer;

    public TcServerHandler(TcServer tcServer) {
        this.tcServer = tcServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("TC client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TseCommand command) throws Exception {
        tcServer.processTseCommand(command);
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
