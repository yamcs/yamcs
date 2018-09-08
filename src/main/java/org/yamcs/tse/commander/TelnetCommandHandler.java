package org.yamcs.tse.commander;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

public class TelnetCommandHandler {

    private static final char[] HEXCHARS = "0123456789ABCDEF".toCharArray();

    private List<Device> devices;

    private boolean printHex;

    private Device connectedDevice;

    public TelnetCommandHandler(List<Device> devices) {
        this.devices = devices;
    }

    public void execute(String cmd, StringWriter out) throws IOException, InterruptedException {
        if (connectedDevice != null) {
            commandDevice(cmd, out);
        } else if (cmd.equals("?") || cmd.equals("help")) {
            out.write(getHelpString());
        } else if (cmd.equals("list")) {
            listDevices(cmd, out);
        } else if (cmd.startsWith("describe")) {
            describeDevice(cmd, out);
        } else if (cmd.startsWith("connect")) {
            connectDevice(cmd, out);
        } else if (!cmd.isEmpty()) {
            out.write(cmd.trim() + ": command not found");
        }
    }

    public void connectDevice(String cmd, StringWriter out) throws IOException {
        String deviceId = cmd.split("\\s+", 2)[1];
        Device device = findDevice(deviceId);
        if (device != null) {
            device.connect();
            connectedDevice = device;
        } else {
            out.write("unknown device");
        }
    }

    public void commandDevice(String cmd, StringWriter out) throws IOException, InterruptedException {
        if ("\\q".equals(cmd)) {
            disconnectDevice();
        } else if ("\\hex".equals(cmd)) {
            printHex = true;
        } else if ("\\ascii".equals(cmd)) {
            printHex = false;
        } else if ("help".equals(cmd) || "?".equals(cmd)) {
            out.write(getDeviceHelpString());
        } else {
            System.out.format("%s <<< %s\n", connectedDevice.getId(), cmd);
            connectedDevice.write(cmd);
            if (cmd.contains("?") || cmd.contains("!")) { // Should maybe make this configurable
                String response = connectedDevice.read();
                if (response != null) {
                    System.out.format("%s >>> %s\n", connectedDevice.getId(), response);
                    if (printHex) {
                        out.write(toHex(response.getBytes()));
                    } else {
                        out.write(response);
                    }
                }
            }
        }
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

    public void listDevices(String cmd, StringWriter out) {
        out.write(String.format("%-20s %s\n", "ID", "DESCRIPTION"));
        String table = devices.stream()
                .map(d -> String.format("%-20s %s", d.getId(), d.getDescription()))
                .collect(Collectors.joining("\n"));
        out.write(table);
    }

    public void describeDevice(String cmd, StringWriter out) {
        String deviceId = cmd.split("\\s+", 2)[1];
        Device device = findDevice(deviceId);
        if (device != null) {
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
            out.write(buf.toString());
        } else {
            out.write("unknown device");
        }
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

    private Device findDevice(String id) {
        for (Device device : devices) {
            if (id.equals(device.getId())) {
                return device;
            }
        }
        return null;
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
