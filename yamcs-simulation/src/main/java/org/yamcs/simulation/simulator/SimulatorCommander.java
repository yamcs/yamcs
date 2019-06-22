package org.yamcs.simulation.simulator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
        this(YConfiguration.emptyConfig());
    }

    /**
     * Constructor used when the simulator is started as an instance service
     * 
     * @param yamcsInstance
     * @param args
     */
    public SimulatorCommander(String yamcsInstance, YConfiguration args) {
        super(superArgs(args));
    }

    public SimulatorCommander(YConfiguration args) {
        super(superArgs(args));
    }

    private static Map<String, Object> superArgs(YConfiguration userArgs) {
        SimulatorArgs defaultOptions = new SimulatorArgs();
        List<String> cmdl = new ArrayList<>();

        cmdl.add(new File(System.getProperty("java.home"), "bin/java").toString());
        cmdl.add("-cp");
        cmdl.add(System.getProperty("java.class.path"));
        cmdl.add(SimulatorCommander.class.getName());
        if (userArgs.containsKey("telnet")) {
            YConfiguration telnetArgs = userArgs.getConfig("telnet");
            int telnetPort = telnetArgs.getInt("port", defaultOptions.telnetPort);
            cmdl.add("--telnet-port");
            cmdl.add(Integer.toString(telnetPort));
        }
        if (userArgs.containsKey("tctm")) {
            YConfiguration yamcsArgs = userArgs.getConfig("tctm");
            int tcPort = yamcsArgs.getInt("tcPort", defaultOptions.tcPort);
            int tmPort = yamcsArgs.getInt("tmPort", defaultOptions.tmPort);
            int losPort = yamcsArgs.getInt("losPort", defaultOptions.losPort);
            int tm2Port = yamcsArgs.getInt("tm2Port", defaultOptions.tm2Port);

            cmdl.addAll(Arrays.asList("--tc-port", "" + tcPort,
                    "--tm-port", "" + tmPort,
                    "--los-port", "" + losPort,
                    "--tm2-port", "" + tm2Port));
        }
        if (userArgs.containsKey("frame")) {
            YConfiguration frameArgs = userArgs.getConfig("frame");
            String tmFrameType = frameArgs.getString("type", defaultOptions.tmFrameType);
            int tmFramePort = frameArgs.getInt("tmPort", defaultOptions.tmFramePort);
            String tmFrameHost = frameArgs.getString("tmHost", defaultOptions.tmFrameHost);
            int tmFrameSize = frameArgs.getInt("tmFrameLength", defaultOptions.tmFrameLength);
            double tmFrameFreq = frameArgs.getDouble("tmFrameFreq", defaultOptions.tmFrameFreq);
            cmdl.addAll(Arrays.asList("--tm-frame-type", "" + tmFrameType,
                    "--tm-frame-host", "" + tmFrameHost,
                    "--tm-frame-port", "" + tmFramePort,
                    "--tm-frame-length", "" + tmFrameSize,
                    "--tm-frame-freq", "" + tmFrameFreq));

        }
        if (userArgs.containsKey("perfTest")) {
            YConfiguration yamcsArgs = userArgs.getConfig("perfTest");
            int numPackets = yamcsArgs.getInt("numPackets", defaultOptions.perfNp);
            if (numPackets > 0) {
                int packetSize = yamcsArgs.getInt("packetSize", defaultOptions.perfPs);
                long interval = yamcsArgs.getLong("interval", defaultOptions.perfMs);
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

        if (runtimeOptions.tmFrameLength > 0) {
            UdpFrameLink frameLink = new UdpFrameLink(runtimeOptions.tmFrameType, runtimeOptions.tmFrameHost,
                    runtimeOptions.tmFramePort,
                    runtimeOptions.tmFrameLength, runtimeOptions.tmFrameFreq);
            services.add(frameLink);
            simulator.setFrameLink(frameLink);
        }

        if (runtimeOptions.perfNp > 0) {
            PerfPacketGenerator ppg = new PerfPacketGenerator(simulator, runtimeOptions.perfNp, runtimeOptions.perfPs,
                    runtimeOptions.perfMs);
            services.add(ppg);
        }
        return services;
    }
}
