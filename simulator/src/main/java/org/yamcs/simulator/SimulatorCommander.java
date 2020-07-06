package org.yamcs.simulator;

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

import org.yamcs.InitException;
import org.yamcs.ProcessRunner;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

public class SimulatorCommander extends ProcessRunner {

    @Override
    public Spec getSpec() {
        Spec telnetSpec = new Spec();
        telnetSpec.addOption("port", OptionType.INTEGER);

        Spec tmtcSpec = new Spec();
        tmtcSpec.addOption("tcPort", OptionType.INTEGER);
        tmtcSpec.addOption("tmPort", OptionType.INTEGER);
        tmtcSpec.addOption("losPort", OptionType.INTEGER);
        tmtcSpec.addOption("tm2Port", OptionType.INTEGER);

        Spec frameSpec = new Spec();
        frameSpec.addOption("type", OptionType.STRING);
        frameSpec.addOption("tmPort", OptionType.INTEGER);
        frameSpec.addOption("tmHost", OptionType.STRING);
        frameSpec.addOption("tmFrameLength", OptionType.INTEGER);
        frameSpec.addOption("tmFrameFreq", OptionType.FLOAT);
        frameSpec.addOption("tcPort", OptionType.INTEGER);

        Spec perfTestSpec = new Spec();
        perfTestSpec.addOption("numPackets", OptionType.INTEGER);
        perfTestSpec.addOption("packetSize", OptionType.INTEGER);
        perfTestSpec.addOption("interval", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("telnet", OptionType.MAP).withSpec(telnetSpec);
        spec.addOption("tctm", OptionType.MAP).withSpec(tmtcSpec);
        spec.addOption("frame", OptionType.MAP).withSpec(frameSpec);
        spec.addOption("perfTest", OptionType.MAP).withSpec(perfTestSpec);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        SimulatorArgs defaultOptions = new SimulatorArgs();
        List<String> cmdl = new ArrayList<>();

        cmdl.add(new File(System.getProperty("java.home"), "bin/java").toString());
        cmdl.add("-cp");
        cmdl.add(System.getProperty("java.class.path"));
        cmdl.add(SimulatorCommander.class.getName());
        if (config.containsKey("telnet")) {
            YConfiguration telnetArgs = config.getConfig("telnet");
            int telnetPort = telnetArgs.getInt("port", defaultOptions.telnetPort);
            cmdl.add("--telnet-port");
            cmdl.add(Integer.toString(telnetPort));
        }
        if (config.containsKey("tctm")) {
            YConfiguration yamcsArgs = config.getConfig("tctm");
            int tcPort = yamcsArgs.getInt("tcPort", defaultOptions.tcPort);
            int tmPort = yamcsArgs.getInt("tmPort", defaultOptions.tmPort);
            int losPort = yamcsArgs.getInt("losPort", defaultOptions.losPort);
            int tm2Port = yamcsArgs.getInt("tm2Port", defaultOptions.tm2Port);

            cmdl.addAll(Arrays.asList("--tc-port", "" + tcPort,
                    "--tm-port", "" + tmPort,
                    "--los-port", "" + losPort,
                    "--tm2-port", "" + tm2Port));
        }
        if (config.containsKey("frame")) {
            YConfiguration frameArgs = config.getConfig("frame");
            String tmFrameType = frameArgs.getString("type", defaultOptions.tmFrameType);
            int tmFramePort = frameArgs.getInt("tmPort", defaultOptions.tmFramePort);
            String tmFrameHost = frameArgs.getString("tmHost", defaultOptions.tmFrameHost);
            int tmFrameSize = frameArgs.getInt("tmFrameLength", defaultOptions.tmFrameLength);
            double tmFrameFreq = frameArgs.getDouble("tmFrameFreq", defaultOptions.tmFrameFreq);
            int tcFramePort = frameArgs.getInt("tcFramePort", defaultOptions.tcFramePort);
            
            cmdl.addAll(Arrays.asList("--tm-frame-type", "" + tmFrameType,
                    "--tm-frame-host", "" + tmFrameHost,
                    "--tm-frame-port", "" + tmFramePort,
                    "--tc-frame-port", "" + tcFramePort,
                    "--tm-frame-length", "" + tmFrameSize,
                    "--tm-frame-freq", "" + tmFrameFreq));
        }
        if (config.containsKey("perfTest")) {
            YConfiguration yamcsArgs = config.getConfig("perfTest");
            int numPackets = yamcsArgs.getInt("numPackets", defaultOptions.perfNp);
            if (numPackets > 0) {
                int packetSize = yamcsArgs.getInt("packetSize", defaultOptions.perfPs);
                long interval = yamcsArgs.getLong("interval", defaultOptions.perfMs);
                cmdl.addAll(Arrays.asList("--perf-np", "" + numPackets,
                        "--perf-ps", "" + packetSize,
                        "--perf-ms", "" + interval));
            }
        }

        try {
            Map<String, Object> processRunnerConfig = new HashMap<>();
            processRunnerConfig.put("command", cmdl);
            processRunnerConfig.put("logPrefix", "");
            processRunnerConfig = super.getSpec().validate(processRunnerConfig);
            super.init(yamcsInstance, YConfiguration.wrap(processRunnerConfig));
        } catch (ValidationException e) {
            throw new InitException(e.getMessage());
        }
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
        TcpTmTcLink tmLink = new TcpTmTcLink("TM", simulator, runtimeOptions.tmPort);
        services.add(tmLink);
        simulator.setTmLink(tmLink);

        TcpTmTcLink tm2Link = new TcpTmTcLink("TM2", simulator, runtimeOptions.tm2Port);
        services.add(tm2Link);
        simulator.setTm2Link(tm2Link);

        TcpTmTcLink losLink = new TcpTmTcLink("LOS", simulator, runtimeOptions.losPort);
        services.add(losLink);
        simulator.setLosLink(losLink);

        services.add(new TcpTmTcLink("TC", simulator, runtimeOptions.tcPort));

        TelnetServer telnetServer = new TelnetServer(simulator);
        telnetServer.setPort(runtimeOptions.telnetPort);
        services.add(telnetServer);

        if (runtimeOptions.tmFrameLength > 0) {
            UdpTcFrameLink tcFrameLink = new UdpTcFrameLink(simulator, runtimeOptions.tcFramePort);
            UdpTmFrameLink frameLink = new UdpTmFrameLink(runtimeOptions.tmFrameType, runtimeOptions.tmFrameHost,
                    runtimeOptions.tmFramePort,
                    runtimeOptions.tmFrameLength, runtimeOptions.tmFrameFreq, () -> {
                        return tcFrameLink.getClcw();
                    });
            services.add(tcFrameLink);
            services.add(frameLink);
            simulator.setTmFrameLink(frameLink);
        }

        if (runtimeOptions.perfNp > 0) {
            PerfPacketGenerator ppg = new PerfPacketGenerator(simulator, runtimeOptions.perfNp, runtimeOptions.perfPs,
                    runtimeOptions.perfMs);
            services.add(ppg);
        }
        return services;
    }
}
