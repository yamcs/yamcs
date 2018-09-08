package com.spaceapplications.yamcs.scpi.commander;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class DeviceCommander {

    public static void main(String[] args) throws InterruptedException {
        new DeviceCommander().start();
    }

    public void start() throws InterruptedException {
        YConfiguration yconf = YConfiguration.getConfiguration("devices");

        List<Device> devices = new ArrayList<>();

        if (yconf.containsKey("devices")) {
            for (Entry<String, Object> entry : yconf.getMap("devices").entrySet()) {
                @SuppressWarnings("unchecked")
                Device device = parseDevice(entry.getKey(), (Map<String, Object>) entry.getValue());
                devices.add(device);
            }
        }

        TelnetServer telnetServer = new TelnetServer(devices);
        if (yconf.containsKey("telnet", "port")) {
            int port = yconf.getInt("telnet", "port");
            telnetServer.setPort(port);
        }
        telnetServer.start();
    }

    private static Device parseDevice(String id, Map<String, Object> config) {
        String locator = YConfiguration.getString(config, "locator");
        String[] parts = locator.split(":", 2);
        if (parts.length < 2) {
            throw new ConfigurationException(String.format(
                    "Invalid locator '%s' for device '%s'. Expecting locator similar to serial:/dev/ttyUSB0.",
                    locator, id));
        }

        String type = parts[0];
        String descriptor = parts[1];

        Device device;
        switch (type) {
        case "serial":
            SerialDevice sDevice = new SerialDevice(id, descriptor);

            if (config.containsKey("baudrate")) {
                sDevice.setBaudrate(YConfiguration.getInt(config, "baudrate"));
            }
            if (config.containsKey("dataBits")) {
                sDevice.setDataBits(YConfiguration.getInt(config, "dataBits"));
            }
            if (config.containsKey("parity")) {
                sDevice.setParity(YConfiguration.getString(config, "parity"));
            }
            device = sDevice;
            break;
        case "tcpip":
            String[] hostAndPort = parts[1].split(":");
            device = new TcpIpDevice(id, hostAndPort[0], Integer.parseInt(hostAndPort[1]));
            break;
        default:
            String msg = "Unknown device type \"{0}\" for device \"{1}\". Supported device types: serial, tcpip";
            throw new ConfigurationException(MessageFormat.format(msg, type, id));
        }

        if (config.containsKey("description")) {
            device.setDescription(YConfiguration.getString(config, "description"));
        }
        // if (config.containsKey("responseTermination")) {
        // device.setResponseTermination(YConfiguration.getInteger(config, "responseTermination"));
        // }
        if (config.containsKey("responseTimeout")) {
            device.setResponseTimeout(YConfiguration.getLong(config, "responseTimeout"));
        }

        return device;
    }
}
