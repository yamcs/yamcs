package org.yamcs.tse;

import java.io.StringWriter;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.StringConverter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TelnetServerHandler.class);

    private static final String PROMPT = "> ";

    private InstrumentController instrumentController;
    private boolean printHex;
    private InstrumentDriver currentInstrument;

    public TelnetServerHandler(InstrumentController instrumentController) {
        this.instrumentController = instrumentController;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Telnet client connected: " + ctx.channel().remoteAddress());
        ctx.writeAndFlush("Yamcs TSE Commander. Run '?' for more info.\n" + PROMPT);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
        cmd = cmd.trim();
        StringWriter out = new StringWriter();
        if (cmd.equals("?")) {
            out.write(getHelpString());
        } else if (cmd.equals("list")) {
            listInstruments(cmd, out);
        } else if (cmd.startsWith("describe")) {
            describeInstrument(cmd, out);
        } else if (cmd.startsWith("use")) {
            useInstrument(cmd, out);
        } else if ("\\hex".equals(cmd)) {
            printHex = true;
        } else if ("\\ascii".equals(cmd)) {
            printHex = false;
        } else if (!cmd.isEmpty()) {
            if (currentInstrument != null) {
                commandInstrument(cmd, out);
            } else {
                out.write(cmd + ": command not found");
            }
        }

        String response = out.toString();
        if (!response.isEmpty()) {
            ctx.write(response);
            ctx.write("\n");
        }

        if (currentInstrument != null) {
            ctx.writeAndFlush(currentInstrument.getName() + PROMPT);
        } else {
            ctx.writeAndFlush(PROMPT);
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

    private void useInstrument(String cmd, StringWriter out) {
        String instrumentId = cmd.split("\\s+", 2)[1];
        InstrumentDriver instrument = instrumentController.getInstrument(instrumentId);
        if (instrument != null) {
            currentInstrument = instrument;
        } else {
            out.write("unknown instrument");
        }
    }

    private void commandInstrument(String cmd, StringWriter out) throws InterruptedException {
        try {
            String result = instrumentController.queueCommand(currentInstrument, cmd).get();
            if (result != null) {
                out.write(printHex ? StringConverter.arrayToHexString(result.getBytes()) : result);
            }
        } catch (ExecutionException e) {
            out.write("error: " + e.getCause().getMessage());
        }
    }

    private void listInstruments(String cmd, StringWriter out) {
        out.write(instrumentController.getInstruments().stream()
                .map(d -> d.getName())
                .sorted()
                .collect(Collectors.joining("\n")));
    }

    private void describeInstrument(String cmd, StringWriter out) {
        String name = cmd.split("\\s+", 2)[1];
        InstrumentDriver instrument = instrumentController.getInstrument(name);
        if (instrument != null) {
            StringBuilder buf = new StringBuilder();
            buf.append("class: ").append(instrument.getClass().getName()).append("\n");
            if (instrument instanceof SerialPortDriver) {
                SerialPortDriver sInstrument = (SerialPortDriver) instrument;
                buf.append("baudrate: ").append(sInstrument.getBaudrate()).append("\n");
                buf.append("data bits: ").append(sInstrument.getDataBits()).append("\n");
                if (sInstrument.getParity() != null) {
                    buf.append("parity: ").append(sInstrument.getParity()).append("\n");
                } else {
                    buf.append("parity: none\n");
                }
            }
            buf.append("response timeout (ms): ").append(instrument.getResponseTimeout()).append("\n");
            if (instrument.getResponseTermination() != null) {
                String hex = StringConverter.arrayToHexString(instrument.getResponseTermination().getBytes());
                buf.append("response termination: 0x").append(hex);
            } else {
                buf.append("response termination: none");
            }
            out.write(buf.toString());
        } else {
            out.write("unknown instrument");
        }
    }

    private String getHelpString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Available commands:\n");
        buf.append("    list             List available instruments\n");
        buf.append("    describe <name>  Print instrument options\n");
        buf.append("    use <name>       Set current instrument\n");
        buf.append("\n");
        buf.append("    \\ascii           Print the ASCII value of instrument responses (default)\n");
        buf.append("    \\hex             Print the hexadecimal value of instrument responses\n");
        buf.append("\n");
        buf.append("    Any other command is sent to the selected instrument.");
        return buf.toString();
    }
}
