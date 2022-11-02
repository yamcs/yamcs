package org.yamcs.tse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.yamcs.FileBasedConfigurationResolver;
import org.yamcs.InitException;
import org.yamcs.ProcessRunner;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

public class TseCommander extends ProcessRunner {

    @Override
    public Spec getSpec() {
        Spec telnetSpec = new Spec();
        telnetSpec.addOption("port", OptionType.INTEGER);

        Spec tmtcSpec = new Spec();
        tmtcSpec.addOption("port", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("telnet", OptionType.MAP).withSpec(telnetSpec);
        spec.addOption("tctm", OptionType.MAP).withSpec(tmtcSpec);
        spec.addOption("instruments", OptionType.LIST).withElementType(OptionType.ANY);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        YConfiguration telnetArgs = config.getConfig("telnet");
        int telnetPort = telnetArgs.getInt("port");

        YConfiguration yamcsArgs = config.getConfig("tctm");
        int tctmPort = yamcsArgs.getInt("port");
        Path configDirectory = YamcsServer.getServer().getConfigDirectory();

        try {
            Map<String, Object> processRunnerConfig = new HashMap<>();
            processRunnerConfig.put("command", Arrays.asList(
                    new File(System.getProperty("java.home"), "bin/java").toString(),
                    TseCommander.class.getName(),
                    "--etc-dir", configDirectory.toString(),
                    "--telnet-port", "" + telnetPort,
                    "--tctm-port", "" + tctmPort));
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
        TseCommanderArgs runtimeOptions = new TseCommanderArgs();
        new JCommander(runtimeOptions).parse(args);

        configureLogging();
        TimeEncoding.setUp();

        YConfiguration.setResolver(new FileBasedConfigurationResolver(runtimeOptions.configDirectory));
        YConfiguration yconf = YConfiguration.getConfiguration("tse");

        List<Service> services = createServices(yconf, runtimeOptions);

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
            for (YConfiguration instrumentConfig : yconf.getConfigList("instruments")) {
                String name = instrumentConfig.getString("name");
                String instrumentClass = instrumentConfig.getString("class");
                YConfiguration instrumentArgs = YConfiguration.emptyConfig();
                if (instrumentConfig.containsKey("args")) {
                    instrumentArgs = instrumentConfig.getConfig("args");
                }
                InstrumentDriver instrument = YObjectLoader.loadObject(instrumentClass);
                instrument.init(name, instrumentArgs);
                instrumentController.addInstrument(instrument);
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
