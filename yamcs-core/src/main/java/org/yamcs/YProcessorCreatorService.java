package org.yamcs;

import java.util.Map;

import org.yamcs.parameter.RealtimeParameterService;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Used in yamcs.instance.yaml to create channels at yamcs startup
 * @author nm
 *
 */
public class YProcessorCreatorService extends AbstractService {
    String channelName;
    String channelType;
    String channelSpec;

    YProcessor yproc;
    String yamcsInstance;

    RealtimeParameterService realtimeParameterService;
    
    
    public YProcessorCreatorService(String yamcsInstance, Map<String, String> config) throws ConfigurationException, StreamSqlException, YProcessorException, ParseException {
	this.yamcsInstance = yamcsInstance;

	if(!config.containsKey("type")) {
	    throw new ConfigurationException("Did not specify the yproc type");
	}
	this.channelType = config.get("type");
	if(!config.containsKey("name")) {
	    throw new ConfigurationException("Did not specify the yproc name");
	}
	this.channelName = config.get("name");

	if(config.containsKey("spec")) {
	    channelSpec = config.get("spec");
	}

    }
    @Override
    protected void doStart() {
	try {
	    yproc =  ProcessorFactory.create(yamcsInstance, channelName, channelType, "system", channelSpec);
	    yproc.setPersistent(true);
	    realtimeParameterService = new RealtimeParameterService(yproc);
	    yproc.start();
	    notifyStarted();
	} catch (Exception e) {
	    notifyFailed(e);
	}
    }

    @Override
    protected void doStop() {
	yproc.quit();
	realtimeParameterService.quit();
	notifyStopped();
    }
}
