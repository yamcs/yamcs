package org.yamcs.tse;

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
import org.yamcs.utils.YObjectLoader;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

public class TseCommander extends ProcessRunner {

    public TseCommander() {
        this(Collections.emptyMap());
    }

    public TseCommander(Map<String, Object> args) {
        super(superArgs(args));
    }

    private static Map<String, Object> superArgs(Map<String, Object> userArgs) {
        Map<String, Object> telnetArgs = YConfiguration.getMap(userArgs, "telnet");
        int telnetPort = YConfiguration.getInt(telnetArgs, "port");

        Map<String, Object> yamcsArgs = YConfiguration.getMap(userArgs, "tctm");
        int tctmPort = YConfiguration.getInt(yamcsArgs, "port");

        Map<String, Object> args = new HashMap<>();
        args.put("command", Arrays.asList("bin/tse-commander.sh",
                "--telnet-port", "" + telnetPort,
                "--tctm-port", "" + tctmPort));
        args.put("logPrefix", "");
        return args;
    }

    public static void main(String[] args) {
        TseCommanderArgs runtimeOptions = new TseCommanderArgs();
        new JCommander(runtimeOptions).parse(args);

        configureLogging();
        TimeEncoding.setUp();

        YConfiguration yconf = YConfiguration.getConfiguration("tse");
        List<Service> services = createServices(yconf, runtimeOptions);

        ServiceManager serviceManager = new ServiceManager(services);
        serviceManager.addListener(new ServiceManager.Listener() {
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

    private static List<Service> createServices(YConfiguration yconf, TseCommanderArgs runtimeOptions) {
        List<Service> services = new ArrayList<>();

        InstrumentController instrumentController = new InstrumentController();
        if (yconf.containsKey("instruments")) {
            for (Object entry : yconf.getList("instruments")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = ((Map<String, Object>) entry);
                String name = YConfiguration.getString(m, "name");
                try {
                    InstrumentDriver instrument = YObjectLoader.loadObject(m, name);
                    instrumentController.addInstrument(instrument);
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }
        services.add(instrumentController);

        TelnetServer telnetServer = new TelnetServer(runtimeOptions.telnetPort, instrumentController);
        services.add(telnetServer);

        if (runtimeOptions.tctmPort != null) {
            services.add(new TcTmServer(runtimeOptions.tctmPort, instrumentController));
        }

        return services;
    }
}
