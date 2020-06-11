package org.yamcs.tse;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tse.api.TseCommand;
import org.yamcs.utils.StringConverter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TelnetServerHandler.class);

    private InstrumentController instrumentController;
    private boolean printHex;
    private InstrumentDriver currentInstrument;

    public TelnetServerHandler(InstrumentController instrumentController) {
        this.instrumentController = instrumentController;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Telnet client connected: " + ctx.channel().remoteAddress());

        String names = instrumentController.getInstruments().stream()
                .map(d -> d.instrument)
                .collect(Collectors.joining(", "));

        ctx.write("======================================================================\r\n");
        ctx.write("Yamcs TSE Commander.\r\n");
        ctx.write("======================================================================\r\n");
        ctx.write("Syntax:\r\n");
        ctx.write("  :tse:instrument <name>\r\n");
        ctx.write("  :tse:instrument?\r\n");
        ctx.write("      Get or set current instrument.\r\n");
        ctx.write("      <name> is one of: " + names + "\r\n");
        ctx.write("\r\n");
        ctx.write("  :tse:output:mode ascii|hex\r\n");
        ctx.write("  :tse:output:mode?\r\n");
        ctx.write("      Get or set output mode of instrument responses.\r\n");
        ctx.write("\r\n");
        ctx.write("Any other command is sent to the selected instrument.\r\n");
        ctx.write("======================================================================\r\n");
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
        cmd = cmd.trim();
        if (cmd.startsWith(":tse")) {
            handleRootCommand(ctx, cmd);
        } else if (!cmd.isEmpty()) {
            if (currentInstrument != null) {
                handleInstrumentCommand(ctx, cmd);
            } else {
                ctx.writeAndFlush("Current instrument is not set. Use ':tse:instrument <name>'.\r\n");
            }
        }
    }

    private void handleRootCommand(ChannelHandlerContext ctx, String cmd) {
        String[] parts = cmd.split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
        case ":tse:instrument":
            String name = parts[1];
            InstrumentDriver instrument = instrumentController.getInstrument(name);
            if (instrument != null) {
                currentInstrument = instrument;
            } else {
                ctx.writeAndFlush("unknown instrument\r\n");
            }
            break;
        case ":tse:instrument?":
            if (currentInstrument != null) {
                ctx.write(currentInstrument.instrument);
            }
            ctx.writeAndFlush("\r\n");
            break;
        case ":tse:output:mode":
            String mode = parts[1];
            if (mode.equals("hex")) {
                printHex = true;
            } else if (mode.equals("ascii")) {
                printHex = false;
            } else {
                ctx.writeAndFlush("unsupported mode\r\n");
            }
            break;
        case ":tse:output:mode?":
            ctx.write(printHex ? "hex" : "ascii");
            ctx.writeAndFlush("\r\n");
            break;
        default:
            ctx.writeAndFlush("syntax error\r\n");
        }
    }

    private void handleInstrumentCommand(ChannelHandlerContext ctx, String cmd) throws InterruptedException {
        // TODO should probably make this configurable
        boolean expectResponse = cmd.contains("?") || cmd.contains("!");
        TseCommand metadata = TseCommand.newBuilder()
                .setInstrument(currentInstrument.instrument)
                .build();
        ListenableFuture<List<String>> f = instrumentController.queueCommand(currentInstrument, metadata, cmd,
                expectResponse);
        f.addListener(() -> {
            try {
                List<String> responses = f.get();
                for (String response : responses) {
                    ctx.write(printHex ? StringConverter.arrayToHexString(response.getBytes()) : response);
                    ctx.writeAndFlush("\r\n");
                }
            } catch (ExecutionException e) {
                String message = e.getCause().getMessage();
                if (message == null) {
                    message = e.getCause().getClass().getName();
                }
                log.warn(message, e.getCause());
                ctx.write("error: " + message);
                ctx.writeAndFlush("\r\n");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, MoreExecutors.directExecutor());
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
