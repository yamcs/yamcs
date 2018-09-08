package org.yamcs.tse.commander;

import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class TseCommander {

    private DevicePool devicePool;

    private TelnetServer telnetServer;
    private RpcServer rpcServer;

    public TseCommander() {
        YConfiguration yconf = YConfiguration.getConfiguration("tse");

        devicePool = new DevicePool();
        if (yconf.containsKey("devices")) {
            for (Entry<String, Object> entry : yconf.getMap("devices").entrySet()) {
                @SuppressWarnings("unchecked")
                Device device = parseDevice(entry.getKey(), (Map<String, Object>) entry.getValue());
                devicePool.add(device);
            }
        }

        TelnetServer telnetServer = new TelnetServer(devicePool);
        if (yconf.containsKey("telnet", "port")) {
            int port = yconf.getInt("telnet", "port");
            telnetServer.setPort(port);
        }

        rpcServer = new RpcServer(devicePool);
    }

    public void start() throws InterruptedException {
        telnetServer.start();
        rpcServer.start();
    }

    private static Device parseDevice(String id, Map<String, Object> config) {
        String locator = YConfiguration.getString(config, "locator");
        String[] parts = locator.split(":", 2);
        if (parts.length < 2) {
            throw new ConfigurationException(String.format(
                    "Invalid locator '%s' for device '%s'. Expecting locator similar to serial:/dev/ttyUSB0",
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
            throw new ConfigurationException(String.format(
                    "Unknown device type '%s' for device '%s'. Use one of: serial, tcpip", type, id));
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

    public static void main(String[] args) throws InterruptedException {
        new TseCommander().start();
    }
}
