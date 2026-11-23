package org.yamcs.simulator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.ProcessRunner;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.SdlsSecurityAssociationFactory;
import org.yamcs.simulator.pus.Pus11ScheduleConfig;
import org.yamcs.simulator.pus.PusMdbTelemetry;
import org.yamcs.simulator.pus.PusPacketConfig;
import org.yamcs.simulator.pus.PusSimulator;
import org.yamcs.simulator.pus.PusTimeEncoding;
import org.yamcs.simulator.pus.PusTimeEncoding.Epoch;
import org.yamcs.simulator.UdpTcFrameLink.TcEncoding;
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
        frameSpec.addOption("tcEncoding", OptionType.STRING);
        frameSpec.addOption("tcMaxFrameLength", OptionType.INTEGER);
        frameSpec.addOption("tcErrorDetection", OptionType.STRING).withChoices("CRC16", "NONE");
        frameSpec.addOption("tmErrorDetection", OptionType.STRING).withChoices("CRC16", "NONE");
        frameSpec.addOption("tmOcfPresent", OptionType.BOOLEAN);
        frameSpec.addOption("uslpTcPort", OptionType.INTEGER);
        frameSpec.addOption("encryption", OptionType.MAP).withSpec(frameEncryptionSpec);

        Spec pusTimeEncodingSpec = new Spec();
        pusTimeEncodingSpec.addOption("type", OptionType.STRING);
        pusTimeEncodingSpec.addOption("epoch", OptionType.STRING);
        pusTimeEncodingSpec.addOption("pfield", OptionType.INTEGER);
        pusTimeEncodingSpec.addOption("pfieldCont", OptionType.INTEGER);
        pusTimeEncodingSpec.addOption("implicitPfield", OptionType.BOOLEAN);

        Spec storageTimeSpec = new Spec();
        storageTimeSpec.addOption("pField", OptionType.INTEGER);
        storageTimeSpec.addOption("pFieldCont", OptionType.INTEGER);
        storageTimeSpec.addOption("epoch", OptionType.STRING);

        Spec pusSpec = new Spec();
        pusSpec.addOption("storageTimeEncoding", OptionType.MAP).withSpec(storageTimeSpec);
        pusSpec.addOption("timeEncoding", OptionType.MAP).withSpec(pusTimeEncodingSpec);
        pusSpec.addOption("apid", OptionType.INTEGER);
        pusSpec.addOption("spareBytes", OptionType.INTEGER);
        pusSpec.addOption("packetStoreSizeUnitBytes", OptionType.INTEGER);
        pusSpec.addOption("tmPacketCrc", OptionType.BOOLEAN);
        pusSpec.addOption("tcPacketCrc", OptionType.BOOLEAN);
        pusSpec.addOption("embeddedTcCrc", OptionType.BOOLEAN);
        pusSpec.addOption("pus22WrapperCrc", OptionType.BOOLEAN);

        Spec telemetrySpec = new Spec();
        telemetrySpec.addOption("mdbFile", OptionType.STRING);
        telemetrySpec.addOption("mdbFiles", OptionType.LIST).withElementType(OptionType.STRING);
        telemetrySpec.requireOneOf("mdbFile", "mdbFiles");
        telemetrySpec.mutuallyExclusive("mdbFile", "mdbFiles");
        telemetrySpec.addOption("housekeepingPeriodMillis", OptionType.INTEGER);
        telemetrySpec.addOption("eventPeriodMillis", OptionType.INTEGER);
        pusSpec.addOption("telemetry", OptionType.MAP).withSpec(telemetrySpec);

        Spec pus11IdSpec = new Spec();
        pus11IdSpec.addOption("bytes", OptionType.INTEGER);
        pus11IdSpec.addOption("default", OptionType.INTEGER);
        Spec scheduleSpec = new Spec();
        scheduleSpec.addOption("wrapperCrc", OptionType.BOOLEAN);
        scheduleSpec.addOption("implicitPfield", OptionType.BOOLEAN);
        scheduleSpec.addOption("subScheduleId", OptionType.MAP).withSpec(pus11IdSpec);
        scheduleSpec.addOption("groupId", OptionType.MAP).withSpec(pus11IdSpec);
        pusSpec.addOption("pus11", OptionType.MAP).withSpec(scheduleSpec);

        Spec perfTestSpec = new Spec();
        perfTestSpec.addOption("numPackets", OptionType.INTEGER);
        perfTestSpec.addOption("packetSize", OptionType.INTEGER);
        perfTestSpec.addOption("interval", OptionType.INTEGER);
        perfTestSpec.addOption("changePercent", OptionType.FLOAT);

        Spec spec = new Spec();
        spec.addOption("telnet", OptionType.MAP).withSpec(telnetSpec);
        spec.addOption("tctm", OptionType.MAP).withSpec(tmtcSpec);
        spec.addOption("frame", OptionType.MAP).withSpec(frameSpec);
        spec.addOption("pus", OptionType.MAP).withSpec(pusSpec);
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
            int scid = frameArgs.getInt("scid", defaultOptions.scid);
            String tmFrameType = frameArgs.getString("type", defaultOptions.tmFrameType);
            int tmFramePort = frameArgs.getInt("tmPort", defaultOptions.tmFramePort);
            String tmFrameHost = frameArgs.getString("tmHost", defaultOptions.tmFrameHost);
            int tmFrameSize = frameArgs.getInt("tmFrameLength", defaultOptions.tmFrameLength);
            double tmFrameFreq = frameArgs.getDouble("tmFrameFreq", defaultOptions.tmFrameFreq);
            int tcFramePort = frameArgs.getInt("tcPort", defaultOptions.tcFramePort);
            String tcFrameEncoding = frameArgs.getString("tcEncoding", defaultOptions.tcFrameEncoding);
            int tcMaxFrameLength = frameArgs.getInt("tcMaxFrameLength", defaultOptions.tcMaxFrameLength);
            String tcFrameErrorDetection = frameArgs.getString("tcErrorDetection", defaultOptions.tcFrameErrorDetection);
            String tmFrameErrorDetection = frameArgs.getString("tmErrorDetection", defaultOptions.tmFrameErrorDetection);
            boolean tmOcfPresent = frameArgs.getBoolean("tmOcfPresent", defaultOptions.tmOcfPresent);

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

            int uslpTcFramePort = frameArgs.getInt("uslpTcPort", defaultOptions.uslpTcFramePort);
            cmdl.addAll(Arrays.asList("--scid", "" + scid,
                    "--tm-frame-type", "" + tmFrameType,
                    "--tm-frame-host", "" + tmFrameHost,
                    "--tm-frame-port", "" + tmFramePort,
                    "--tc-frame-port", "" + tcFramePort,
                    "--tc-frame-encoding", "" + tcFrameEncoding,
                    "--tc-max-frame-length", "" + tcMaxFrameLength,
                    "--tc-frame-error-detection", tcFrameErrorDetection,
                    "--tm-frame-error-detection", tmFrameErrorDetection,
                    "--tm-ocf-present", "" + tmOcfPresent,
                    "--uslp-tc-frame-port", "" + uslpTcFramePort,
                    "--tm-frame-length", "" + tmFrameSize,
                    "--tm-frame-freq", "" + tmFrameFreq));
        }

        if (config.containsKey("pus")) {
            YConfiguration pusArgs = config.getConfig("pus");
            boolean tcCrc = pusArgs.getBoolean("tcPacketCrc", defaultOptions.pusTcPacketCrc);
            cmdl.addAll(List.of("--pus-apid", Integer.toString(pusArgs.getInt("apid", defaultOptions.pusApid)),
                    "--pus-spare-bytes", Integer.toString(pusArgs.getInt("spareBytes", defaultOptions.pusSpareBytes)),
                    "--pus-packet-store-size-unit-bytes", Integer.toString(
                            pusArgs.getInt("packetStoreSizeUnitBytes", defaultOptions.pusPacketStoreSizeUnitBytes)),
                    "--pus-tm-packet-crc", Boolean.toString(pusArgs.getBoolean("tmPacketCrc", defaultOptions.pusTmPacketCrc)),
                    "--pus-tc-packet-crc", Boolean.toString(tcCrc),
                    "--pus22-wrapper-crc", Boolean.toString(pusArgs.getBoolean("pus22WrapperCrc", tcCrc)),
                    "--pus-embedded-tc-crc", Boolean.toString(pusArgs.getBoolean("embeddedTcCrc", tcCrc))));
            if (pusArgs.containsKey("telemetry")) {
                YConfiguration telemetry = pusArgs.getConfig("telemetry");
                List<String> files = telemetry.containsKey("mdbFiles") ? telemetry.getList("mdbFiles")
                        : List.of(telemetry.getString("mdbFile"));
                if (files.isEmpty()) {
                    throw new InitException("pus.telemetry.mdbFiles must contain at least one XTCE file");
                }
                for (String file : files) {
                    cmdl.addAll(List.of("--pus-telemetry-mdb", file));
                }
                cmdl.addAll(List.of(
                        "--pus-housekeeping-period", Integer.toString(telemetry.getInt("housekeepingPeriodMillis",
                                defaultOptions.pusHousekeepingPeriod)),
                        "--pus-event-period", Integer.toString(telemetry.getInt("eventPeriodMillis",
                                defaultOptions.pusEventPeriod))));
            }
            if (pusArgs.containsKey("pus11")) {
                YConfiguration schedule = pusArgs.getConfig("pus11");
                cmdl.addAll(List.of("--pus11-wrapper-crc", Boolean.toString(schedule.getBoolean("wrapperCrc", tcCrc))));
                if (schedule.containsKey("implicitPfield")) {
                    cmdl.addAll(List.of("--pus11-tc-implicit-p-field",
                            Boolean.toString(schedule.getBoolean("implicitPfield"))));
                }
                addPus11IdOptions(cmdl, schedule, "subScheduleId", "subschedule");
                addPus11IdOptions(cmdl, schedule, "groupId", "group");
            }
            if (pusArgs.containsKey("storageTimeEncoding")) {
                YConfiguration storageTime = pusArgs.getConfig("storageTimeEncoding");
                cmdl.addAll(Arrays.asList(
                        "--pus-storage-time-pfield", "" + storageTime.getInt("pField", defaultOptions.pusStorageTimePfield),
                        "--pus-storage-time-pfield-cont", "" + storageTime.getInt("pFieldCont", defaultOptions.pusStorageTimePfieldCont),
                        "--pus-storage-time-epoch", storageTime.getString("epoch", defaultOptions.pusStorageTimeEpoch)));
            }
            if (pusArgs.containsKey("timeEncoding")) {
                YConfiguration timeEncodingArgs = pusArgs.getConfig("timeEncoding");
                cmdl.addAll(Arrays.asList(
                        "--pus-time-type", timeEncodingArgs.getString("type", defaultOptions.pusTimeType),
                        "--pus-time-epoch", timeEncodingArgs.getString("epoch", defaultOptions.pusTimeEpoch),
                        "--pus-time-pfield", "" + timeEncodingArgs.getInt("pfield", defaultOptions.pusTimePfield),
                        "--pus-time-pfield-cont",
                        "" + timeEncodingArgs.getInt("pfieldCont", defaultOptions.pusTimePfieldCont)));
                if (timeEncodingArgs.getBoolean("implicitPfield", defaultOptions.pusTimeImplicitPfield)) {
                    cmdl.add("--pus-time-implicit-pfield");
                }
            }
        }

        if ("pus".equalsIgnoreCase(config.getString("type", defaultOptions.type))
                && !config.containsKey("tctm")
                && config.containsKey("frame")) {
            cmdl.add("--no-tcp-tmtc");
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

    private static void createDirectory(File dir, String description) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LoggerFactory.getLogger(SimulatorCommander.class)
                    .warn("Could not create {} directory {}", description, dir.getAbsolutePath());
        }
    }

    private static boolean frameCrcEnabled(String errorDetection, String option) {
        return switch (errorDetection.toUpperCase(Locale.ROOT)) {
        case "CRC16" -> true;
        case "NONE" -> false;
        default -> throw new ConfigurationException("Unsupported simulator " + option + " " + errorDetection);
        };
    }

    private static void addPus11IdOptions(List<String> args, YConfiguration schedule, String key, String option) {
        if (schedule.containsKey(key)) {
            YConfiguration id = schedule.getConfig(key);
            args.addAll(List.of("--pus11-" + option + "-bytes", Integer.toString(id.getInt("bytes", 1)),
                    "--pus11-" + option + "-default", Long.toString(id.getLong("default", 0))));
        }
    }

    public static List<Service> createServices(SimulatorArgs runtimeOptions) {
        TcPacketFactory pktFactory;
        AbstractSimulator simulator;
        File losDir = runtimeOptions.losDir.toFile();
        createDirectory(losDir, "LOS");
        File dataDir = runtimeOptions.dataDir.toFile();
        createDirectory(dataDir, "data");

        if (runtimeOptions.noTcpTmtc) {
            runtimeOptions.tmPort = null;
            runtimeOptions.tm2Port = null;
            runtimeOptions.losPort = null;
            runtimeOptions.tcPort = null;
        }

        if (runtimeOptions.type.equalsIgnoreCase("col")) {
            pktFactory = TcPacketFactory.COL_PACKET_FACTORY;
            simulator = new ColSimulator(losDir, dataDir);
        } else if (runtimeOptions.type.equalsIgnoreCase("pus")) {
            PusTimeEncoding timeEncoding = new PusTimeEncoding(
                    runtimeOptions.pusTimePfield,
                    runtimeOptions.pusTimePfieldCont,
                    runtimeOptions.pusTimeImplicitPfield,
                    Epoch.valueOf(runtimeOptions.pusTimeEpoch.toUpperCase()));
            if (!"CUC".equalsIgnoreCase(runtimeOptions.pusTimeType)) {
                throw new ConfigurationException("Unsupported PUS time encoding type '" + runtimeOptions.pusTimeType
                        + "'. Only CUC is supported");
            }
            PusTimeEncoding storageTime = new PusTimeEncoding(runtimeOptions.pusStorageTimePfield,
                    runtimeOptions.pusStorageTimePfieldCont, true,
                    Epoch.valueOf(runtimeOptions.pusStorageTimeEpoch.toUpperCase()));
            boolean tcCrc = runtimeOptions.pusTcPacketCrc;
            PusPacketConfig packetConfig = new PusPacketConfig(runtimeOptions.pusApid, runtimeOptions.pusSpareBytes,
                    runtimeOptions.pusTmPacketCrc, tcCrc,
                    runtimeOptions.pus11WrapperCrc == null ? tcCrc : runtimeOptions.pus11WrapperCrc,
                    runtimeOptions.pus22WrapperCrc == null ? tcCrc : runtimeOptions.pus22WrapperCrc,
                    runtimeOptions.pusEmbeddedTcCrc == null ? tcCrc : runtimeOptions.pusEmbeddedTcCrc,
                    runtimeOptions.pusPacketStoreSizeUnitBytes);
            PusTimeEncoding scheduleTime = runtimeOptions.pus11TcImplicitPfield == null ? timeEncoding
                    : new PusTimeEncoding(runtimeOptions.pusTimePfield, runtimeOptions.pusTimePfieldCont,
                            runtimeOptions.pus11TcImplicitPfield, timeEncoding.epoch());
            Pus11ScheduleConfig scheduleConfig = new Pus11ScheduleConfig(scheduleTime,
                    runtimeOptions.pus11SubScheduleBytes, runtimeOptions.pus11SubScheduleDefault,
                    runtimeOptions.pus11GroupBytes, runtimeOptions.pus11GroupDefault);
            pktFactory = TcPacketFactory.pusPacketFactory(packetConfig);
            var telemetryMdbFiles = runtimeOptions.pusTelemetryMdbFiles;
            PusSimulator pus = new PusSimulator(dataDir, timeEncoding, storageTime, packetConfig, scheduleConfig,
                    telemetryMdbFiles.isEmpty());
            if (!telemetryMdbFiles.isEmpty()) {
                pus.configureMdbTelemetry(PusMdbTelemetry.load(telemetryMdbFiles),
                        runtimeOptions.pusHousekeepingPeriod, runtimeOptions.pusEventPeriod);
            }
            simulator = pus;
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

        if (runtimeOptions.tmFrameLength > 0) {
            if (simulator instanceof PusSimulator && !"TM".equalsIgnoreCase(runtimeOptions.tmFrameType)) {
                throw new ConfigurationException("PUS simulator frame.type supports only TM for now; got "
                        + runtimeOptions.tmFrameType);
            }
            if (simulator instanceof PusSimulator && runtimeOptions.uslpTcFramePort > 0) {
                throw new ConfigurationException("PUS simulator does not support USLP TC frame ingest for now");
            }
            // Load a key for encryption/decryption if one was provided
            final SdlsSecurityAssociation maybeSdlsTm, maybeSdlsTc;

            short spi = (short) runtimeOptions.encryptionSpi;
            String encryptionClass = runtimeOptions.encryptionClass;
            if (encryptionClass != null) {
                String argStr = runtimeOptions.encryptionArgs;
                Yaml yaml = new Yaml();
                Map<String, Object> args = Arrays.stream(argStr.split(":"))
                        .map(s -> s.split("="))
                        .collect(Collectors.toMap(a -> a[0], a -> yaml.load(a[1])));
                YConfiguration argsConfig = new YConfiguration(null, null, args);

                ServiceLoader<SdlsSecurityAssociationFactory> loader = ServiceLoader
                        .load(SdlsSecurityAssociationFactory.class);
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

            TcEncoding tcEncoding = TcEncoding.valueOf(runtimeOptions.tcFrameEncoding.toUpperCase(Locale.ROOT));
            boolean tcFrameCrc = frameCrcEnabled(runtimeOptions.tcFrameErrorDetection, "tcErrorDetection");
            boolean tmFrameCrc = frameCrcEnabled(runtimeOptions.tmFrameErrorDetection, "tmErrorDetection");
            int expectedScid = simulator instanceof PusSimulator ? runtimeOptions.scid : -1;
            UdpTcFrameLink tcFrameLink = new UdpTcFrameLink(simulator, runtimeOptions.tcFramePort, maybeSdlsTc,
                    pktFactory, tcEncoding, tcFrameCrc, runtimeOptions.tcMaxFrameLength, expectedScid);

            UdpUslpFrameLink uslpFrameLink = null;
            if (simulator instanceof ColSimulator colSimulator) {
                if (runtimeOptions.uslpTcFramePort > 0) {
                    uslpFrameLink = new UdpUslpFrameLink(colSimulator, runtimeOptions.uslpTcFramePort);
                    services.add(uslpFrameLink);
                }
            }

            final UdpUslpFrameLink finalUslpFrameLink = uslpFrameLink;
            IntSupplier clcwSupplier = finalUslpFrameLink != null
                    ? finalUslpFrameLink::getClcw
                    : tcFrameLink::getClcw;
            UdpTmFrameLink frameLink = new UdpTmFrameLink(runtimeOptions.scid, runtimeOptions.tmFrameType,
                    runtimeOptions.tmFrameHost, runtimeOptions.tmFramePort, runtimeOptions.tmFrameLength,
                    runtimeOptions.tmFrameFreq,
                    clcwSupplier, maybeSdlsTm, runtimeOptions.tmOcfPresent, tmFrameCrc);

            services.add(tcFrameLink);
            services.add(frameLink);
            simulator.setTmFrameLink(frameLink);
        }

        if (simulator instanceof ColSimulator colSimulator) {
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
