package org.yamcs;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.artemis.RealtimeArtemisParameterService;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Used in yamcs.instance.yaml to create processors at yamcs startup
 * @author nm
 *
 */
public class ProcessorCreatorService extends AbstractService {
    String processorName;
    String processorType;
    String processorConfig;

    Processor yproc;
    String yamcsInstance;

    final boolean startArtemisService;
    RealtimeArtemisParameterService realtimeParameterService;
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ProcessorCreatorService(String yamcsInstance, Map<String, String> config) throws ConfigurationException, StreamSqlException, ProcessorException, ParseException {
	this.yamcsInstance = yamcsInstance;

	if(!config.containsKey("type")) {
	    throw new ConfigurationException("Did not specify the processor type");
	}
	this.processorType = config.get("type");
	if(!config.containsKey("name")) {
	    throw new ConfigurationException("Did not specify the processor name");
	}
	this.processorName = config.get("name");

	if(config.containsKey("config")) {
            processorConfig = config.get("config");
        } else if(config.containsKey("spec")) {
	    processorConfig = config.get("spec");
	}
	startArtemisService = YConfiguration.getBoolean((Map)config, "startArtemisService", false);
	log.debug("Creating a new processor instance:{}, procName: {}, procType: {}", yamcsInstance, processorName, processorType);
	yproc =  ProcessorFactory.create(yamcsInstance, processorName, processorType, "system", processorConfig);
        yproc.setPersistent(true);
    }
    @Override
    protected void doStart() {
	try {
	    log.debug("Starting processor {}", processorName);
	    if(startArtemisService) {
	        realtimeParameterService = new RealtimeArtemisParameterService(yproc);
	    }
	    yproc.start();
	    notifyStarted();
	} catch (Exception e) {
	    log.error("Starting a new processor {}.{} failed: {}", yamcsInstance, processorName, e.getMessage());
	    notifyFailed(e);
	}
    }

    @Override
    protected void doStop() {
        yproc.quit();
        if(realtimeParameterService!=null) {
            realtimeParameterService.quit();
       }
	notifyStopped();
    }
}
