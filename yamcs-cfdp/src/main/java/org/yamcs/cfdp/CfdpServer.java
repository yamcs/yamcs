package org.yamcs.cfdp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.yamcs.YConfiguration;
import org.yamcs.server.ProcessRunner;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

public class CfdpServer extends ProcessRunner {

    public CfdpServer() {
        this(Collections.emptyMap());
    }

    public CfdpServer(Map<String, Object> args) {
        super(superArgs(args));
    }

    private static Map<String, Object> superArgs(Map<String, Object> userArgs) {
        Map<String, Object> args = new HashMap<>();

        args.put("command", Arrays.asList("bin/cfdp-server.sh"));
        return args;
    }

    public static void main(String[] args) {
        CfdpServerArgs runtimeOptions = new CfdpServerArgs();
        new JCommander(runtimeOptions).parse(args);

        configureLogging();
        TimeEncoding.setUp();

        YConfiguration yconf = YConfiguration.getConfiguration("cfdp");
        List<Service> services = createServices(yconf, runtimeOptions);

        ServiceManager serviceManager = new ServiceManager(services);
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(Service service) {
                System.exit(1);
            }
        });

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
            try (InputStream in = CfdpServer.class.getResourceAsStream("/cfdp-logging.properties")) {
                logManager.readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Failed to set up logging configuration: " + e.getMessage());
        }
    }

    private static List<Service> createServices(YConfiguration yconf, CfdpServerArgs runtimeOptions) {
        List<Service> services = new ArrayList<>();
        services.add(new DummyCfdpService());
        return services;
    }
}
