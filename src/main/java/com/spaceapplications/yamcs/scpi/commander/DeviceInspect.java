package com.spaceapplications.yamcs.scpi.commander;

import java.text.MessageFormat;
import java.util.Optional;

import com.spaceapplications.yamcs.scpi.Config;

public class DeviceInspect extends Command {
    private Config config;

    public DeviceInspect(String cmd, String description, HasContext context, Config config) {
        super(cmd, description, context);
        this.config = config;
    }

    @Override
    String handleExecute(String deviceId) {
        return Optional.ofNullable(config.devices).map(devices -> devices.get(deviceId)).map(Config::dump)
                .orElse(MessageFormat.format("device \"{0}\" not found", deviceId));
    }
}
