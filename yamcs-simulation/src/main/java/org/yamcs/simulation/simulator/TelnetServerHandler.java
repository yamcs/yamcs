package org.yamcs.simulation.simulator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
        String[] parts = cmd.trim().split("\\s+", 2);
        switch (parts[0].toUpperCase()) {
        case "*IDN?":
            ctx.writeAndFlush("Space Applications Services,Yamcs Demo Simulator\r\n");
            break;
        case ":LOS?":
            ctx.write(simulator.getLosStart() != null ? "1" : "0");
            ctx.writeAndFlush("\r\n");
            break;
        case ":LOS:STAR":
        case ":LOS:START":
            simulator.setLOS();
            break;
        case ":LOS:STOP":
            simulator.setAOS();
            break;
        case ":LOS:DATE?":
            Date start = simulator.getLosStart();
            if (start == null) {
                ctx.write("0,0,0");
                ctx.writeAndFlush("\r\n");
            } else {
                ctx.write(formatDate(start));
                ctx.writeAndFlush("\r\n");
            }
            break;
        case ":LOS:TIME?":
            Date time = simulator.getLosStart();
            if (time == null) {
                ctx.write("0,0,0");
                ctx.writeAndFlush("\r\n");
            } else {
                ctx.write(formatTime(time));
                ctx.writeAndFlush("\r\n");
            }
            break;
        case ":DATE?":
            ctx.write(formatDate(new Date()));
            ctx.writeAndFlush("\r\n");
            break;
        case ":TIME?":
            ctx.write(formatTime(new Date()));
            ctx.writeAndFlush("\r\n");
            break;
        default:
            ctx.writeAndFlush("unrecognized command\r\n");
        }
    }

    private static String formatDate(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy,MM,dd");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

    private static String formatTime(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("HH,mm,ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
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
