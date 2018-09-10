package org.yamcs.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TelnetServerHandler.class);

    private Simulator simulator;

    public TelnetServerHandler(Simulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Telnet client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
        cmd = cmd.trim().toUpperCase();

        if (cmd.equals("*IDN")) {
            ctx.writeAndFlush("Yamcs Demo Simulator\r\n");
        } else if (cmd.equals(":LOS0")) {
            simulator.setLOS(false);
        } else if (cmd.equals(":LOS1")) {
            simulator.setLOS(true);
        } else if (cmd.equals(":LOS?")) {
            ctx.write(simulator.isLOS() ? "1" : "0");
            ctx.writeAndFlush("\r\n");
        } else {
            ctx.writeAndFlush("unrecognized command\r\n");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Closing channel due to exception", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Telnet client disconnected: " + ctx.channel().remoteAddress());
    }
}
