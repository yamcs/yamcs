package org.yamcs.simulator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.ProcessRunner;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.SdlsSecurityAssociationFactory;
import org.yamcs.simulator.pus.PusSimulator;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

/**
 * Starts the simulator.
 * <p>
 * This class is configured as a service inside Yamcs but it starts itself as an external process via the
 * {@link #main(String[])} function.
 *
 */
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

        Spec frameEncryptionSpec = new Spec();
        frameEncryptionSpec.addOption("class", OptionType.STRING).withRequired(true);
        frameEncryptionSpec.addOption("args", OptionType.ANY);
        frameEncryptionSpec.addOption("spi", OptionType.INTEGER).withRequired(true);

        Spec frameSpec = new Spec();
        frameSpec.addOption("scid", OptionType.INTEGER);
        frameSpec.addOption("type", OptionType.STRING);
        frameSpec.addOption("tmPort", OptionType.INTEGER);
        frameSpec.addOption("tmHost", OptionType.STRING);
        frameSpec.addOption("tmFrameLength", OptionType.INTEGER);
        frameSpec.addOption("tmFrameFreq", OptionType.FLOAT);
        frameSpec.addOption("tcPort", OptionType.INTEGER);
        frameSpec.addOption("encryption", OptionType.MAP).withSpec(frameEncryptionSpec);

        Spec perfTestSpec = new Spec();
        perfTestSpec.addOption("numPackets", OptionType.INTEGER);
        perfTestSpec.addOption("packetSize", OptionType.INTEGER);
        perfTestSpec.addOption("interval", OptionType.INTEGER);
        perfTestSpec.addOption("changePercent", OptionType.FLOAT);

        Spec spec = new Spec();
        spec.addOption("telnet", OptionType.MAP).withSpec(telnetSpec);
        spec.addOption("tctm", OptionType.MAP).withSpec(tmtcSpec);
        spec.addOption("frame", OptionType.MAP).withSpec(frameSpec);
        spec.addOption("perfTest", OptionType.MAP).withSpec(perfTestSpec);

        spec.addOption("type", OptionType.STRING);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        SimulatorArgs defaultOptions = new SimulatorArgs();
        List<String> cmdl = new ArrayList<>();

        cmdl.add(new File(System.getProperty("java.home"), "bin/java").toString());
        // Debugging:
        // cmdl.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8989");
        cmdl.add(SimulatorCommander.class.getName());
        if (config.containsKey("telnet")) {
            YConfiguration telnetArgs = config.getConfig("telnet");
            int telnetPort = telnetArgs.getInt("port", defaultOptions.telnetPort);
            cmdl.add("--telnet-port");
            cmdl.add(Integer.toString(telnetPort));
        }

        if (config.containsKey("scid")) {
            cmdl.add("--scid");
            cmdl.add(Integer.toString(config.getInt("scid")));
        }

        if (config.containsKey("type")) {
            cmdl.add("--type");
            cmdl.add(config.getString("type"));
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

            if (frameArgs.containsKey("encryption")) {
                YConfiguration frameEncryption = frameArgs.getConfig("encryption");
                short encryptionSpi = (short) frameEncryption.getInt("spi", defaultOptions.encryptionSpi);

                String encryptionClass = frameEncryption.getString("class", defaultOptions.encryptionClass);
                Map<String, String> encryptionArgs = frameEncryption.getMap("args");

                cmdl.addAll(Arrays.asList(
                        "--encryption-class", encryptionClass,
                        "--encryption-spi", "" + encryptionSpi));
                // Pass all custom encryption arguments in the format:
                // --encryption-args arg1=val1:arg2=val2
                List<String> sArgs = encryptionArgs.entrySet().stream()
                                        .map(e -> e.getKey() + "=" + e.getValue())
                                        .toList();
                cmdl.addAll(Arrays.asList("--encryption-args", String.join(":", sArgs)));
            }

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
                double changePercent = yamcsArgs.getDouble("changePercent", defaultOptions.perfChangePercent);
                cmdl.addAll(Arrays.asList("--perf-np", "" + numPackets,
                        "--perf-ps", "" + packetSize,
                        "--perf-ms", "" + interval,
                        "--perf-cp", "" + changePercent));
            }
        }

        try {
            Map<String, Object> processRunnerConfig = new HashMap<>();
            processRunnerConfig.put("command", cmdl);
            processRunnerConfig.put("logPrefix", "");
            Map<String, Object> processEnvironment = new HashMap<>();
            processEnvironment.put("CLASSPATH", System.getProperty("java.class.path"));
            processRunnerConfig.put("environment", processEnvironment);
            processRunnerConfig = super.getSpec().validate(processRunnerConfig);
            super.init(yamcsInstance, serviceName, YConfiguration.wrap(processRunnerConfig));
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
                service.failureCause().printStackTrace(System.err);
                System.exit(1);
            }
        }, MoreExecutors.directExecutor());

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

    public static List<Service> createServices(SimulatorArgs runtimeOptions) {
        TcPacketFactory pktFactory;
        AbstractSimulator simulator;
        File losDir = runtimeOptions.losDir.toFile();
        losDir.mkdirs();
        File dataDir = runtimeOptions.dataDir.toFile();
        dataDir.mkdirs();

        if (runtimeOptions.type.equalsIgnoreCase("col")) {
            pktFactory = TcPacketFactory.COL_PACKET_FACTORY;
            simulator = new ColSimulator(losDir, dataDir);
        } else if (runtimeOptions.type.equalsIgnoreCase("pus")) {
            pktFactory = TcPacketFactory.PUS_PACKET_FACTORY;
            simulator = new PusSimulator(dataDir);
        } else {
            throw new ConfigurationException("Unknown simulator type '" + runtimeOptions.type + "'. Use COL or PUS");
        }

        List<Service> services = new ArrayList<>();
        services.add(simulator);
        if (runtimeOptions.tmPort != null) {
            TcpTmTcLink tmLink = new TcpTmTcLink("TM", simulator, runtimeOptions.tmPort, pktFactory);
            services.add(tmLink);
            simulator.setTmLink(tmLink);
        }

        if (runtimeOptions.tm2Port != null) {
            TcpTmTcLink tm2Link = new TcpTmTcLink("TM2", simulator, runtimeOptions.tm2Port, pktFactory);
            services.add(tm2Link);
            simulator.setTm2Link(tm2Link);
        }

        if (runtimeOptions.losPort != null) {
            TcpTmTcLink losLink = new TcpTmTcLink("LOS", simulator, runtimeOptions.losPort, pktFactory);
            services.add(losLink);
            simulator.setLosLink(losLink);
        }

        if (runtimeOptions.tcPort != null) {
            services.add(new TcpTmTcLink("TC", simulator, runtimeOptions.tcPort, pktFactory));
        }

        if (simulator instanceof ColSimulator colSimulator) {
            if (runtimeOptions.telnetPort != null) {
                TelnetServer telnetServer = new TelnetServer(colSimulator);
                telnetServer.setPort(runtimeOptions.telnetPort);
                services.add(telnetServer);
            }
        }

        if (simulator instanceof ColSimulator colSimulator) {
            if (runtimeOptions.tmFrameLength > 0) {
                // Load a key for encryption/decryption if one was provided
                final SdlsSecurityAssociation maybeSdlsTm, maybeSdlsTc;

                short spi = (short) runtimeOptions.encryptionSpi;
                String encryptionClass = runtimeOptions.encryptionClass;
                if (encryptionClass != null) {
                    String argStr = runtimeOptions.encryptionArgs;
                    Yaml yaml = new Yaml();
                    Map<String, Object> args =
                            Arrays.stream(argStr.split(":"))
                                    .map(s -> s.split("="))
                                    .collect(Collectors.toMap(a -> a[0], a -> yaml.load(a[1])));
                    YConfiguration argsConfig = new YConfiguration(null, null, args);


                    ServiceLoader<SdlsSecurityAssociationFactory> loader = ServiceLoader.load(SdlsSecurityAssociationFactory.class);
                    Optional<ServiceLoader.Provider<SdlsSecurityAssociationFactory>> maybeSaImpl = loader.stream()
                            .filter(l -> l.get().getClass().getName().equals(encryptionClass))
                            .findFirst();
                    if (maybeSaImpl.isEmpty()) {
                        throw new ConfigurationException("No implementation of SdlsSecurityAssociationFactory found " +
                                "for " + encryptionClass);
                    }
                    SdlsSecurityAssociationFactory saImpl = maybeSaImpl.get().get();

                    maybeSdlsTc = saImpl.create(null, "TC", spi, argsConfig);
                    maybeSdlsTm = saImpl.create(null, "TM", spi, argsConfig);

                } else {
                    maybeSdlsTm = null;
                    maybeSdlsTc = null;
                }

                UdpTcFrameLink tcFrameLink = new UdpTcFrameLink(colSimulator, runtimeOptions.tcFramePort, maybeSdlsTc);
                UdpTmFrameLink frameLink = new UdpTmFrameLink(runtimeOptions.scid, runtimeOptions.tmFrameType,
                        runtimeOptions.tmFrameHost,
                        runtimeOptions.tmFramePort,
                        runtimeOptions.tmFrameLength, runtimeOptions.tmFrameFreq, () -> {
                            return tcFrameLink.getClcw();
                        }, maybeSdlsTm);

                services.add(tcFrameLink);
                services.add(frameLink);
                colSimulator.setTmFrameLink(frameLink);
            }

            if (runtimeOptions.perfNp > 0) {
                PerfPacketGenerator ppg = new PerfPacketGenerator(
                        colSimulator,
                        runtimeOptions.perfNp,
                        runtimeOptions.perfPs,
                        runtimeOptions.perfMs,
                        runtimeOptions.perfChangePercent);
                colSimulator.setPerfPacketGenerator(ppg);
                services.add(ppg);
            }
        }
        return services;
    }
}