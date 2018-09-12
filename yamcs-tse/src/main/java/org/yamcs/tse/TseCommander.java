package org.yamcs.tse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

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
            System.err.println("Failed to set up logging configuration: " + e.getMessage());
        }
    }

    private static List<Service> createServices(YConfiguration yconf) {
        List<Service> services = new ArrayList<>();

        DeviceManager deviceManager = new DeviceManager();
        if (yconf.containsKey("devices")) {
            for (Object entry : yconf.getList("devices")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = ((Map<String, Object>) entry);
                String name = YConfiguration.getString(m, "name");
                try {
                    Device device = YObjectLoader.loadObject(m, name);
                    deviceManager.add(device);
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }
        services.add(deviceManager);

        if (yconf.containsKey("telnet")) {
            TelnetServer telnetServer = new TelnetServer(deviceManager);
            if (yconf.containsKey("telnet", "port")) {
                int port = yconf.getInt("telnet", "port");
                telnetServer.setPort(port);
            }
            services.add(telnetServer);
        }

        if (yconf.containsKey("rpc")) {
            RpcServer rpcServer = new RpcServer(deviceManager);
            if (yconf.containsKey("rpc", "port")) {
                int port = yconf.getInt("rpc", "port");
                rpcServer.setPort(port);
            }
            services.add(rpcServer);
        }

        return services;
    }
}
