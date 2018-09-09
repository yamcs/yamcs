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

    private DeviceManager deviceManager;
    private boolean printHex;
    private Device currentDevice;

    public TelnetServerHandler(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
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
            listDevices(cmd, out);
        } else if (cmd.startsWith("describe")) {
            describeDevice(cmd, out);
        } else if (cmd.startsWith("use")) {
            useDevice(cmd, out);
        } else if ("\\hex".equals(cmd)) {
            printHex = true;
        } else if ("\\ascii".equals(cmd)) {
            printHex = false;
        } else if (!cmd.isEmpty()) {
            if (currentDevice != null) {
                commandDevice(cmd, out);
            } else {
                out.write(cmd + ": command not found");
            }
        }

        String response = out.toString();
        if (!response.isEmpty()) {
            ctx.write(response);
            ctx.write("\n");
        }

        if (currentDevice != null) {
            ctx.writeAndFlush(currentDevice.getName() + PROMPT);
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

    private void useDevice(String cmd, StringWriter out) {
        String deviceId = cmd.split("\\s+", 2)[1];
        Device device = deviceManager.getDevice(deviceId);
        if (device != null) {
            currentDevice = device;
        } else {
            out.write("unknown device");
        }
    }

    private void commandDevice(String cmd, StringWriter out) throws InterruptedException {
        try {
            String result = deviceManager.queueCommand(currentDevice, cmd).get();
            if (result != null) {
                out.write(printHex ? StringConverter.arrayToHexString(result.getBytes()) : result);
            }
        } catch (ExecutionException e) {
            log.error("Failed to execute command", e.getCause());
            out.write("error: " + e.getCause().getMessage());
        }
    }

    private void listDevices(String cmd, StringWriter out) {
        out.write(deviceManager.getDevices().stream()
                .map(d -> d.getName())
                .sorted()
                .collect(Collectors.joining("\n")));
    }

    private void describeDevice(String cmd, StringWriter out) {
        String name = cmd.split("\\s+", 2)[1];
        Device device = deviceManager.getDevice(name);
        if (device != null) {
            StringBuilder buf = new StringBuilder();
            buf.append("class: ").append(device.getClass().getName()).append("\n");
            if (device instanceof SerialDevice) {
                SerialDevice sDevice = (SerialDevice) device;
                buf.append("baudrate: ").append(sDevice.getBaudrate()).append("\n");
                buf.append("data bits: ").append(sDevice.getDataBits()).append("\n");
                if (sDevice.getParity() != null) {
                    buf.append("parity: ").append(sDevice.getParity()).append("\n");
                } else {
                    buf.append("parity: none\n");
                }
            }
            buf.append("response timeout (ms): ").append(device.getResponseTimeout()).append("\n");
            if (device.getResponseTermination() != null) {
                String hex = StringConverter.arrayToHexString(device.getResponseTermination().getBytes());
                buf.append("response termination: 0x").append(hex);
            } else {
                buf.append("response termination: none");
            }
            out.write(buf.toString());
        } else {
            out.write("unknown device");
        }
    }

    private String getHelpString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Available commands:\n");
        buf.append("    list             List available devices\n");
        buf.append("    describe <name>  Print device configuration details\n");
        buf.append("    use <name>       Set current device\n");
        buf.append("\n");
        buf.append("    \\ascii           Print the ASCII value of device responses (default)\n");
        buf.append("    \\hex             Print the hexadecimal value of device responses\n");
        buf.append("\n");
        buf.append("    Any other command is sent to the selected device.");
        return buf.toString();
    }
}
