package org.yamcs.cli;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Spec;
import org.yamcs.Spec.ValidationContext;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsService;
import org.yamcs.utils.YObjectLoader;

import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Check Yamcs configuration")
public class CheckConfig extends Command {

    private static final Logger log = LoggerFactory.getLogger(CheckConfig.class);

    public CheckConfig(YamcsAdminCli parent) {
        super("confcheck", parent);
    }

    @Override
    void execute() throws Exception {
        YamcsServer yamcs = YamcsServer.getServer();

        try {
            log.debug("Validating yamcs.yaml ...");
            yamcs.validateMainConfiguration();

            // Global services
            YConfiguration config = yamcs.getConfig();
            if (config.containsKey("services")) {
                log.debug("Validating global services ...");
                for (YConfiguration serviceConfig : config.getServiceConfigList("services")) {
                    validateServiceConfig(serviceConfig);
                }
            }

            if (config.containsKey("instances")) {
                log.debug("Validating instances ...");
                for (String name : config.<String> getList("instances")) {
                    YConfiguration instanceConfig = YConfiguration.getConfiguration("yamcs." + name);
                    YamcsServerInstance.getSpec().validate(instanceConfig);
                    if (instanceConfig.containsKey("services")) {
                        log.debug("Validating instance services ...");
                        for (YConfiguration serviceConfig : instanceConfig.getServiceConfigList("services")) {
                            validateServiceConfig(serviceConfig);
                        }
                    }
                }
            }

            console.println("Configuration OK");
        } catch (ValidationException e) {
            console.println(e.getContext().getPath() + ": " + e.getMessage());
            console.println("Configuration Invalid");
        }
    }

    private void validateServiceConfig(YConfiguration serviceConfig) throws ValidationException, IOException {
        String serviceClass = serviceConfig.getString("class");
        log.debug(serviceClass);
        try {
            YamcsService service = YObjectLoader.loadObject(serviceClass);
            Spec spec = service.getSpec();
            if (spec == null) {
                return;
            }

            YConfiguration args = YConfiguration.emptyConfig();
            if (serviceConfig.containsKey("args")) {
                args = serviceConfig.getConfig("args");
            }
            spec.validate(args);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException(new ValidationContext(serviceConfig.getPath()), e.getMessage());
        }
    }
}
