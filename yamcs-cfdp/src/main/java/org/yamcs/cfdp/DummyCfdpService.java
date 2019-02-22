package org.yamcs.cfdp;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsService;
import org.yamcs.web.HttpServer;
import org.yamcs.web.rest.CfdpRestHandler;

import com.google.common.util.concurrent.AbstractService;

public class DummyCfdpService extends AbstractService implements YamcsService {

    private static final Logger log = LoggerFactory.getLogger(DummyCfdpService.class);
    final String yamcsInstance;

    public DummyCfdpService(String yamcsInstance, Map<String, Object> config) {
        log.info("Created new service with config {}", config);
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    protected void doStart() {
        log.info("DummyCfdpService.doStart");

        HttpServer httpServer = YamcsServer.getServer().getGlobalServices(HttpServer.class).get(0);

        httpServer.registerRouteHandler(yamcsInstance, new CfdpRestHandler() {});

        notifyStarted();
    }

    @Override
    protected void doStop() {
        log.info("DummyCfdpService.doStop");
        notifyStopped();
    }

}
