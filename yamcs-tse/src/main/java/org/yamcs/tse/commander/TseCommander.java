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

    private static Device parseDevice(String id, Map<String, Object> args) {
        String locator = YConfiguration.getString(args, "locator");
        String[] parts = locator.split(":", 2);
        if (parts.length < 2) {
            throw new ConfigurationException(String.format("Invalid locator for device '%s'", id));
        }

        Device device;
        switch (parts[0]) {
        case "serial":
            device = new SerialDevice(id, args);
            break;
        case "tcpip":
            device = new TcpIpDevice(id, args);
            break;
        default:
            throw new ConfigurationException(String.format(
                    "Unknown device type '%s' for device '%s'. Use one of: serial, tcpip", parts[0], id));
        }

        return device;
    }
}
