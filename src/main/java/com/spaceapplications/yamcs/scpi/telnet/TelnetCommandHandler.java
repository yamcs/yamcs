package com.spaceapplications.yamcs.scpi.telnet;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.device.Device;

public class TelnetCommandHandler {

    private Config config;
    private List<Device> devices;

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
            return commandDevice(cmd);
        }

        if (cmd.equals("help")) {
            return getHelpString();
        } else if (cmd.equals("list")) {
            String header = String.format("%-20s %s\n", "ID", "DESCRIPTION");
            String devList = config.devices.entrySet().stream()
                    .map(set -> String.format("%-20s %s", set.getKey(), set.getValue().description))
                    .collect(Collectors.joining("\n"));
            return header + devList;
        } else if (cmd.startsWith("describe")) {
            String deviceId = cmd.split("\\s+", 2)[1];
            if (config.devices.containsKey(deviceId)) {
                return Config.dump(config.devices.get(deviceId));
            }
            return "unknown device";
        } else if (cmd.startsWith("connect")) {
            String deviceId = cmd.split("\\s+", 2)[1];
            for (Device device : devices) {
                if (deviceId.equals(device.id())) {
                    connectDevice(device);
                    return "connected to: " + deviceId + " (\\q to exit)";
                }
            }
            return "unknown device";
        }

        return cmd.trim() + ": command not found";
    }

    public void connectDevice(Device device) {
        device.connect();
        this.connectedDevice = device;
    }

    public String commandDevice(String cmd) throws InterruptedException {
        if ("\\q".equals(cmd) && connectedDevice != null) {
            disconnectDevice();
            return "disconnected";
        }
        connectedDevice.write(cmd);
        return connectedDevice.read(3, TimeUnit.SECONDS);
    }

    public void disconnectDevice() {
        if (connectedDevice != null) {
            connectedDevice.disconnect();
            connectedDevice = null;
        }
    }

    public Device getConnectedDevice() {
        return connectedDevice;
    }

    private String getHelpString() {
        StringBuilder buf = new StringBuilder();
        buf.append("    list           List available devices to manage.\n");
        buf.append("    describe <ID>  Print device configuration details.\n");
        buf.append("    connect <ID>   Connect and interact with a device.\n");
        buf.append("    help           Print this description.");
        return buf.toString();
    }
}
