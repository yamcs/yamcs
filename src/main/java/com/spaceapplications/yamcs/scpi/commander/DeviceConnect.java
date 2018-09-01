package com.spaceapplications.yamcs.scpi.commander;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.spaceapplications.yamcs.scpi.device.Device;

public class DeviceConnect extends Command {
    private List<Device> devices = new ArrayList<>();

    public DeviceConnect(String cmd, String description, HasContext context, List<Device> devices) {
        super(cmd, description, context);
        this.devices = devices;
    }

    @Override
    String handleExecute(String deviceId) {
        Optional<Device> device = findDevice(deviceId);
        if (!device.isPresent()) {
            return deviceId + ": device not found";
        }
        device.get().open();
        String prompt = "device:" + deviceId + Command.DEFAULT_PROMPT;
        setPrompt(prompt);
        Command parent = this;

        Command contextCmd = new Command("", "", context) {
            @Override
            String handleExecute(String cmd) {
                if (Commander.isCtrlD(cmd)) {
                    setPrompt(Command.DEFAULT_PROMPT);
                    parent.setPrompt(Command.DEFAULT_PROMPT);
                    context.clearContextCmd();
                    device.get().close();
                    return "\ndisconnect from " + deviceId;
                }
                device.get().write(cmd);
                return device.get().read();
            }
        };

        contextCmd.setPrompt(prompt);
        context.setContextCmd(contextCmd);
        return "connected to: " + deviceId + " (ctrl+d to exit)";
    }

    private Optional<Device> findDevice(String deviceId) {
        return devices.stream().filter(d -> deviceId.equals(d.id())).findFirst();
    }
}
