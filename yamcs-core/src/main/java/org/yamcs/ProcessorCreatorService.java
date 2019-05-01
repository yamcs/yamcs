package org.yamcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Used in yamcs.instance.yaml to create processors at yamcs startup
 * 
 * @author nm
 *
 */
public class ProcessorCreatorService extends AbstractService implements YamcsService {
    String processorName;
    String processorType;
    String processorConfig;

    Processor processor;
    String yamcsInstance;

    private static final Logger log = LoggerFactory.getLogger(ProcessorCreatorService.class);

    public ProcessorCreatorService(String yamcsInstance, YConfiguration config)
            throws ConfigurationException, StreamSqlException, ProcessorException, ParseException {
        this.yamcsInstance = yamcsInstance;

        if (!config.containsKey("type")) {
            throw new ConfigurationException("Did not specify the processor type");
        }
        this.processorType = config.getString("type");
        if (!config.containsKey("name")) {
            throw new ConfigurationException("Did not specify the processor name");
        }
        this.processorName = config.getString("name");

        if (config.containsKey("config")) {
            processorConfig = config.getString("config");
        } else if (config.containsKey("spec")) {
            processorConfig = config.getString("spec");
        }
        log.debug("Creating a new processor instance: {}, procName: {}, procType: {}", yamcsInstance, processorName,
                processorType);
        processor = ProcessorFactory.create(yamcsInstance, processorName, processorType, "system", processorConfig);
        processor.setPersistent(true);
    }

    @Override
    protected void doStart() {
        try {
            log.debug("Starting processor {}", processorName);
            processor.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Starting a new processor {}.{} failed: {}. Cause: {}", yamcsInstance, processorName, e.getMessage(), e.getCause());
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        processor.quit();
        notifyStopped();
    }
}
