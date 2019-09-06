package org.yamcs.tse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tse.api.TseCommand;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TcTmServerHandler extends SimpleChannelInboundHandler<TseCommand> {

    private static final Logger log = LoggerFactory.getLogger(TcTmServerHandler.class);

    private TcTmServer tctmServer;

    public TcTmServerHandler(TcTmServer tctmServer) {
        this.tctmServer = tctmServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("TM/TC client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TseCommand command) throws Exception {
        tctmServer.processTseCommand(ctx, command);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("TM/TC client disconnected: " + ctx.channel().remoteAddress());
    }
}
