package com.spaceapplications.yamcs.scpi;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.spaceapplications.yamcs.scpi.Config.DeviceConfig;
import com.spaceapplications.yamcs.scpi.device.Device;
import com.spaceapplications.yamcs.scpi.device.SerialDevice;
import com.spaceapplications.yamcs.scpi.device.TcpIpDevice;
import com.spaceapplications.yamcs.scpi.telnet.TelnetServer;

public class Main {

    @Parameter(names = "--config", required = true, description = "Load YAML config file.")
    public String config;

    @Parameter(names = "--help", help = true, description = "Print this help message.")
    public boolean help;

    public static void main(String[] args) throws InterruptedException {
        args = new String[] { "--config", "/Users/fdi/workspace/yamcs-scpi/config.yaml" };

        Main main = new Main();

        JCommander jc = new JCommander(main);
        jc.setProgramName("yamcs-scpi");
        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(-1);
        }

        if (main.help) {
            jc.usage();
            System.exit(0);
        }

        main.start();
    }

    public void start() throws InterruptedException {
        Config config = Config.load(this.config);

        List<Device> devices = config.devices.entrySet().stream()
                .map(entry -> parseDevice(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        TelnetServer telnetServer = new TelnetServer(config, devices);
        telnetServer.start();
    }

    private static Device parseDevice(String id, DeviceConfig config) {
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
