package org.yamcs.tse.commander;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ServiceManager.Listener;

public class TseCommander {

    public static void main(String[] args) {
        configureLogging();

        YConfiguration yconf = YConfiguration.getConfiguration("tse");
        List<Service> services = createServices(yconf);

        ServiceManager serviceManager = new ServiceManager(services);
        serviceManager.addListener(new Listener() {
            @Override
            public void failure(Service service) {
                // Stop entire process as soon as one service fails.
                System.exit(1);
            }
        });

        // Allow services to shutdown gracefully
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    serviceManager.stopAsync().awaitStopped(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // ignore
                }
            }
        });

        serviceManager.startAsync();
    }

    private static void configureLogging() {
        try {
            LogManager logManager = LogManager.getLogManager();
            try (InputStream in = TseCommander.class.getResourceAsStream("/tse-logging.properties")) {
                logManager.readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to set up logging configuration: " + e.getMessage());
        }
    }

    private static List<Service> createServices(YConfiguration yconf) {
        DeviceManager deviceManager = new DeviceManager();
        if (yconf.containsKey("devices")) {
            for (Entry<String, Object> entry : yconf.getMap("devices").entrySet()) {
                @SuppressWarnings("unchecked")
                Device device = parseDevice(entry.getKey(), (Map<String, Object>) entry.getValue());
                deviceManager.add(device);
            }
        }

        TelnetServer telnetServer = new TelnetServer(deviceManager);
        if (yconf.containsKey("telnet", "port")) {
            int port = yconf.getInt("telnet", "port");
            telnetServer.setPort(port);
        }

        RpcServer rpcServer = new RpcServer(deviceManager);
        if (yconf.containsKey("rpc", "port")) {
            int port = yconf.getInt("rpc", "port");
            rpcServer.setPort(port);
        }

        return Arrays.asList(deviceManager, telnetServer, rpcServer);
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
}
