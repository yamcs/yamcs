package com.spaceapplications.yamcs.scpi.telnet;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.Device;
import com.spaceapplications.yamcs.scpi.SerialDevice;

public class TelnetCommandHandler {

    private static final char[] HEXCHARS = "0123456789ABCDEF".toCharArray();

    private Config config;
    private List<Device> devices;

    private boolean printHex;

    private Device connectedDevice;

    public TelnetCommandHandler(Config config, List<Device> devices) {
        this.config = config;
        this.devices = devices;
    }

    public String execute(String cmd) throws InterruptedException {
        if (cmd.isEmpty()) {
            return null;
        }
        if (connectedDevice != null) {
            try {
                return commandDevice(cmd);
            } catch (IOException e) {
                e.printStackTrace();
                return "error: " + e.getMessage();
            }
        }

        if (cmd.equals("?") || cmd.equals("help")) {
            return getHelpString();
        } else if (cmd.equals("list")) {
            String header = String.format("%-20s %s\n", "ID", "DESCRIPTION");
            String devList = config.devices.entrySet().stream()
                    .map(set -> String.format("%-20s %s", set.getKey(), set.getValue().description))
                    .collect(Collectors.joining("\n"));
            return header + devList;
        } else if (cmd.startsWith("describe")) {
            String deviceId = cmd.split("\\s+", 2)[1];
            for (Device device : devices) {
                if (deviceId.equals(device.getId())) {
                    return describeDevice(device);
                }
            }
            return "unknown device";
        } else if (cmd.startsWith("connect")) {
            String deviceId = cmd.split("\\s+", 2)[1];
            for (Device device : devices) {
                if (deviceId.equals(device.getId())) {
                    try {
                        connectDevice(device);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return "error: " + e.getMessage();
                    }
                    return null;
                }
            }
            return "unknown device";
        }

        return cmd.trim() + ": command not found";
    }

    public void connectDevice(Device device) throws IOException {
        device.connect();
        this.connectedDevice = device;
    }

    public String commandDevice(String cmd) throws IOException, InterruptedException {
        if ("\\q".equals(cmd) && connectedDevice != null) {
            disconnectDevice();
            return "disconnected";
        } else if ("\\hex".equals(cmd)) {
            printHex = true;
            return null;
        } else if ("\\ascii".equals(cmd)) {
            printHex = false;
        } else if ("help".equals(cmd) || "?".equals(cmd)) {
            return getDeviceHelpString();
        }

        connectedDevice.write(cmd);
        if (cmd.contains("?") || cmd.contains("!")) { // Should maybe make this configurable
            String response = connectedDevice.read();
            if (printHex && response != null) {
                return toHex(response.getBytes());
            } else {
                return response;
            }
        }
        return null;
    }

    public void disconnectDevice() throws IOException {
        if (connectedDevice != null) {
            connectedDevice.disconnect();
            connectedDevice = null;
        }
    }

    public Device getConnectedDevice() {
        return connectedDevice;
    }

    private String describeDevice(Device device) {
        StringBuilder buf = new StringBuilder();
        if (device instanceof SerialDevice) {
            SerialDevice sDevice = (SerialDevice) device;
            buf.append("locator: serial:").append(sDevice.getPath()).append("\n");
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
            buf.append("response termination: ").append(device.getResponseTermination());
        } else {
            buf.append("response termination: none");
        }
        return buf.toString();
    }

    private String getHelpString() {
        StringBuilder buf = new StringBuilder();
        buf.append("    list           List available devices to manage.\n");
        buf.append("    describe <ID>  Print device configuration details.\n");
        buf.append("    connect <ID>   Connect and interact with a device.");
        return buf.toString();
    }

    private String getDeviceHelpString() {
        StringBuilder buf = new StringBuilder();
        buf.append("    \\hex       Print the hexadecimal value of next responses.\n");
        buf.append("    \\ascii     Print the ASCII value of next responses.\n");
        buf.append("    \\q         Disconnect device.\n");
        buf.append("\n");
        buf.append("    Any other command is sent to the connected device.");
        return buf.toString();
    }

    public static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            chars[j * 2] = HEXCHARS[v >>> 4];
            chars[j * 2 + 1] = HEXCHARS[v & 0x0F];
        }
        return new String(chars);
    }
}
