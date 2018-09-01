package com.spaceapplications.yamcs.scpi.telnet;

import java.util.ArrayList;
import java.util.List;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.device.Device;

public class Commander {

    public static final String DEFAULT_PROMPT = "$ ";

    private List<Command> commands = new ArrayList<>();
    private Device connectedDevice;

    public Commander(Config config, List<Device> devices) {
        commands.add(new DeviceInspect("inspect", "Print device configuration details.", this, config));
        commands.add(new DeviceList("list", "List available devices to manage.", this, config));
        commands.add(new DeviceConnect("connect", "Connect and interact with a given device.", this, devices));
        commands.add(new HelpCommand("help", "Prints this description.", this, commands));
    }

    public String execute(String cmd) {
        if (cmd.isEmpty()) {
            return getPrompt();
        }

        if (connectedDevice == null) {
            for (Command command : commands) {
                if (cmd.startsWith(command.cmd())) {
                    return command.execute(cmd) + "\n" + getPrompt();
                }
            }
            return cmd.trim() + ": command not found\n" + getPrompt();
        } else {
            return commandDevice(cmd) + "\n" + getPrompt();
        }
    }

    public void connectDevice(Device device) {
        device.connect();
        this.connectedDevice = device;
    }

    public String commandDevice(String cmd) {
        if ("\\q".equals(cmd) && connectedDevice != null) {
            disconnectDevice();
            return "disconnected";
        }
        connectedDevice.write(cmd);
        return connectedDevice.read();
    }

    public void disconnectDevice() {
        if (connectedDevice != null) {
            connectedDevice.disconnect();
            connectedDevice = null;
        }
    }

    public String getPrompt() {
        if (connectedDevice != null) {
            return connectedDevice.id() + DEFAULT_PROMPT;
        } else {
            return DEFAULT_PROMPT;
        }
    }
}
