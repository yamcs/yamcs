package org.yamcs.web;


import org.yamcs.ConfigurationException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Registers web services for an instance. 
 * 
 * @author nm
 *
 */
public class YamcsWebService extends AbstractService {
    
    private String yamcsInstance;
    private HttpServer server;
    
    public YamcsWebService(String yamcsInstance) throws ConfigurationException, InterruptedException {
        this.yamcsInstance = yamcsInstance;
        this.server = HttpServer.getInstance();        
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
