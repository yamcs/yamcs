package com.spaceapplications.yamcs.scpi.telnet;

import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;

public class DeviceList extends Command {

    Config config;

    public DeviceList(String cmd, String description, Commander commander, Config config) {
        super(cmd, description, commander);
        this.config = config;
    }

    @Override
    String handleExecute(String args) {
        String header = String.format("Available devices:\n%-20s %s\n", "ID", "DESCRIPTION");
        String devList = config.devices.entrySet().stream()
                .map(set -> String.format("%-20s %s", set.getKey(), set.getValue().description))
                .collect(Collectors.joining("\n"));
        return header + devList;
    }
}
