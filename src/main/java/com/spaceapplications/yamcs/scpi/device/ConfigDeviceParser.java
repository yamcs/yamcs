package com.spaceapplications.yamcs.scpi.device;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.Config.DeviceConfig;

public class ConfigDeviceParser {
    private static Map<String, BiFunction<String, DeviceConfig, Device>> typeToDevice = new HashMap<>();

    static {
        typeToDevice.put("serial",
                (id, config) -> new SerialDevice(id, config.locator, Optional.ofNullable(config.baudrate)));
    }

    public static List<Device> get(Config config) {
        return config.devices.entrySet().stream().map(entry -> createDevice(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static Device createDevice(String id, DeviceConfig config) {
        String type = assertDeviceType(id, config);
        return typeToDevice.get(type).apply(id, config);
    }

    private static String assertDeviceType(String id, DeviceConfig config) {
        int index = config.locator.indexOf(":");
        if (index < 0) {
            String msg = MessageFormat.format(
                    "Invalid locator \"{0}\" for device \"{1}\". Expecting locator similar to serial:/dev/ttyUSB0.",
                    config.locator, id);
            throw new RuntimeException(msg);
        }
        String type = config.locator.substring(0, config.locator.indexOf(":"));
        if (!typeToDevice.containsKey(type)) {
            String types = typeToDevice.keySet().toString();
            String msg = "Unknown device type \"{0}\" for device \"{1}\". Supported device types: {2}";
            throw new RuntimeException(MessageFormat.format(msg, type, id, types));
        }
        return type;
    }
}
