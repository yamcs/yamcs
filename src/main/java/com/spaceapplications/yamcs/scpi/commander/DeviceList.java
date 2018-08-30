package com.spaceapplications.yamcs.scpi.commander;

import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;

public class DeviceList extends Command {

    Config config;

    public DeviceList(String cmd, String description, HasContext context, Config config) {
        super(cmd, description, context);
        this.config = config;
    }

    @Override
    String handleExecute(String args) {
        String header = String.format("Available devices:\n" + COL_FORMAT + "\n", "ID", "DESCRIPTION");
        String devList = config.devices.entrySet().stream()
                .map(set -> String.format(COL_FORMAT, set.getKey(), set.getValue().description))
                .collect(Collectors.joining("\n"));
        return header + devList;
    }
}
