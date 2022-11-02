package org.yamcs;

import org.yamcs.Spec.OptionType;
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
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        processorType = config.getString("type");
        processorName = config.getString("name");

        if (config.containsKey("config")) {
            processorConfig = config.getString("config");
        } else if (config.containsKey("spec")) {
            processorConfig = config.getString("spec");
        }
        log.debug("Creating a new processor [instance={}, procName={}, procType={}]", yamcsInstance, processorName,
                processorType);
        try {
            SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
            String systemUser = securityStore.getSystemUser().getName();
            processor = ProcessorFactory.create(yamcsInstance, processorName, processorType, systemUser,
                    processorConfig);
        } catch (ProcessorException | ValidationException e) {
            throw new InitException(e);
        }
        processor.setPersistent(true);
        processor.setProtected(true);
    }

    @Override
    protected void doStart() {
        try {
            log.debug("Starting processor {}", processorName);
            processor.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Starting a new processor {}.{} failed: {}", yamcsInstance, processorName,
                    e.toString(), e);
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        processor.quit();
        notifyStopped();
    }
}
