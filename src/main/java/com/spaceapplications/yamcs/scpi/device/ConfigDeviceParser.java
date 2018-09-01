package com.spaceapplications.yamcs.scpi.device;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.Config.DeviceConfig;
import com.spaceapplications.yamcs.scpi.ConfigurationException;

public class ConfigDeviceParser {

    public static List<Device> get(Config config) {
        return config.devices.entrySet().stream()
                .map(entry -> createDevice(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static Device createDevice(String id, DeviceConfig config) {
        String[] parts = config.locator.split(":", 2);
        if (parts.length < 2) {
            String msg = MessageFormat.format(
                    "Invalid locator \"{0}\" for device \"{1}\". Expecting locator similar to serial:/dev/ttyUSB0.",
                    config.locator, id);
            throw new ConfigurationException(msg);
        }

        String type = parts[0];
        String descriptor = parts[1];

        switch (type) {
        case "serial":
            return new SerialDevice(id, descriptor, Optional.ofNullable(config.baudrate));
        case "tcpip":
            String[] hostAndPort = parts[1].split(":");
            return new TcpIpDevice(id, hostAndPort[0], Integer.parseInt(hostAndPort[1]));
        default:
            String msg = "Unknown device type \"{0}\" for device \"{1}\". Supported device types: serial, tcpip";
            throw new ConfigurationException(MessageFormat.format(msg, type, id));
        }
    }
}
