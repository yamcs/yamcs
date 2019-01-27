package org.yamcs.simulation.simulator;

import java.io.File;
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

public class SimulatorCommander extends ProcessRunner {

    public SimulatorCommander() {
        this(Collections.emptyMap());
    }

    public SimulatorCommander(Map<String, Object> args) {
        super(superArgs(args));
    }

    private static Map<String, Object> superArgs(Map<String, Object> userArgs) {
        SimulatorArgs defaultOptions = new SimulatorArgs();
        List<String> cmdl = new ArrayList<>();

        cmdl.add("bin/simulator.sh");
        if (userArgs.containsKey("telnet")) {
            Map<String, Object> telnetArgs = YConfiguration.getMap(userArgs, "telnet");
            int telnetPort = YConfiguration.getInt(telnetArgs, "port", defaultOptions.telnetPort);
            cmdl.add("--telnet-port");
            cmdl.add(Integer.toString(telnetPort));
        }
        if (userArgs.containsKey("tctm")) {
            Map<String, Object> yamcsArgs = YConfiguration.getMap(userArgs, "tctm");
            int tcPort = YConfiguration.getInt(yamcsArgs, "tcPort", defaultOptions.tcPort);
            int tmPort = YConfiguration.getInt(yamcsArgs, "tmPort", defaultOptions.tmPort);
            int losPort = YConfiguration.getInt(yamcsArgs, "losPort", defaultOptions.losPort);
            cmdl.addAll(Arrays.asList("--tc-port", "" + tcPort,
                    "--tm-port", "" + tmPort,
                    "--los-port", "" + losPort));
        }
        if (userArgs.containsKey("perfTest")) {
            Map<String, Object> yamcsArgs = YConfiguration.getMap(userArgs, "perfTest");
            int numPackets = YConfiguration.getInt(yamcsArgs, "numPackets", defaultOptions.perfNp);
            if (numPackets > 0) {
                int packetSize = YConfiguration.getInt(yamcsArgs, "packetSize", defaultOptions.perfPs);
                long interval = YConfiguration.getLong(yamcsArgs, "interval", defaultOptions.perfMs);
                cmdl.addAll(Arrays.asList("--perf-np", "" + numPackets,
                        "--perf-ps", "" + packetSize,
                        "--perf-ms", "" + interval));
            }
        }

        Map<String, Object> args = new HashMap<>();
        args.put("command", cmdl);
        args.put("logPrefix", "");
        return args;
    }

    public static void main(String[] args) {
        SimulatorArgs runtimeOptions = new SimulatorArgs();
        new JCommander(runtimeOptions).parse(args);

        configureLogging();
        TimeEncoding.setUp();

        List<Service> services = createServices(runtimeOptions);

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
            try (InputStream in = SimulatorCommander.class.getResourceAsStream("/simulator-logging.properties")) {
                logManager.readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Failed to set up logging configuration: " + e.getMessage());
        }
    }

    private static List<Service> createServices(SimulatorArgs runtimeOptions) {
        List<Service> services = new ArrayList<>();
        Simulator simulator = new Simulator(new File("losData"), runtimeOptions.tmPort,
                runtimeOptions.tcPort, runtimeOptions.losPort);
        services.add(simulator);
        TmTcLink tmLink = new TmTcLink("TM", simulator, runtimeOptions.tmPort);
        services.add(tmLink);
        simulator.setTmLink(tmLink);

        TmTcLink tm2Link = new TmTcLink("TM2", simulator, runtimeOptions.tm2Port);
        services.add(tm2Link);
        simulator.setTm2Link(tm2Link);

        TmTcLink losLink = new TmTcLink("LOS", simulator, runtimeOptions.losPort);
        services.add(losLink);
        simulator.setLosLink(losLink);

        services.add(new TmTcLink("TC", simulator, runtimeOptions.tcPort));

        TelnetServer telnetServer = new TelnetServer(simulator);
        telnetServer.setPort(runtimeOptions.telnetPort);
        services.add(telnetServer);

        if (runtimeOptions.perfNp > 0) {
            PerfPacketGenerator ppg = new PerfPacketGenerator(simulator, runtimeOptions.perfNp, runtimeOptions.perfPs,
                    runtimeOptions.perfMs);
            services.add(ppg);
        }

        return services;
    }
}
