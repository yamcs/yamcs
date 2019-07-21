package org.yamcs;

import org.yamcs.api.AbstractYamcsService;
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;
import org.yamcs.security.SecurityStore;

/**
 * Used in yamcs.instance.yaml to create processors at yamcs startup
 * 
 * @author nm
 *
 */
public class ProcessorCreatorService extends AbstractYamcsService {
    String processorName;
    String processorType;
    String processorConfig;

    Processor processor;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("type", OptionType.STRING).withRequired(true);
        spec.addOption("name", OptionType.STRING).withRequired(true);
        spec.addOption("config", OptionType.STRING);
        spec.addOption("spec", OptionType.STRING);

        spec.mutuallyExclusive("config", "spec");
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        processorType = config.getString("type");
        processorName = config.getString("name");

        if (config.containsKey("config")) {
            processorConfig = config.getString("config");
        } else if (config.containsKey("spec")) {
            processorConfig = config.getString("spec");
        }
        log.debug("Creating a new processor instance: {}, procName: {}, procType: {}", yamcsInstance, processorName,
                processorType);
        try {
            SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
            String systemUser = securityStore.getSystemUser().getUsername();
            processor = ProcessorFactory.create(yamcsInstance, processorName, processorType, systemUser,
                    processorConfig);
        } catch (ProcessorException e) {
            throw new InitException(e);
        }
        processor.setPersistent(true);
    }

    @Override
    protected void doStart() {
        try {
            log.debug("Starting processor {}", processorName);
            processor.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Starting a new processor {}.{} failed: {}. Cause: {}", yamcsInstance, processorName,
                    e.getMessage(), e.getCause());
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        processor.quit();
        notifyStopped();
    }
}
