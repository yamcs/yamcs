package org.yamcs.web;


import org.yamcs.ConfigurationException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Starts an HTTP server (if not already started by another instance) and registers this yamcs instance to allow
 * web requests to http://url/instanceName/... 
 * 
 * @author nm
 *
 */
public class YamcsWebService extends AbstractService {
    String yamcsInstance;
    HttpSocketServer server;
    
    public YamcsWebService(String yamcsInstance) throws ConfigurationException {
        this.yamcsInstance=yamcsInstance;
        StaticFileRequestHandler.init();
        this.server = HttpSocketServer.getInstance();
    }

    @Override
    protected void doStart() {
        server.registerYamcsInstance(yamcsInstance, this);
        notifyStarted();
    }

    @Override
    protected void doStop() {
	notifyStopped();
    }
}
