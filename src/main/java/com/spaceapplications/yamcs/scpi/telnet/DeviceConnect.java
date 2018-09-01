package com.spaceapplications.yamcs.scpi.telnet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.spaceapplications.yamcs.scpi.device.Device;

public class DeviceConnect extends Command {

    private Map<String, Device> devices = new HashMap<>();

    public DeviceConnect(String cmd, String description, Commander commander, List<Device> devices) {
        super(cmd, description, commander);
        for (Device device : devices) {
            this.devices.put(device.id(), device);
        }
    }

    @Override
    String handleExecute(String deviceId) {
        Device device = devices.get(deviceId);
        if (device == null) {
            return deviceId + ": device not found";
        }

        commander.connectDevice(device);
        return "connected to: " + deviceId + " (\\q to exit)";
    }
}
