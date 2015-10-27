package org.yamcs.web;


import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.web.rest.AlarmsRequestHandler;
import org.yamcs.web.rest.ArchiveRequestHandler;
import org.yamcs.web.rest.AuthorizationRequestHandler;
import org.yamcs.web.rest.CommandsRequestHandler;
import org.yamcs.web.rest.ContainersRequestHandler;
import org.yamcs.web.rest.EventsRequestHandler;
import org.yamcs.web.rest.ParametersRequestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;

import com.google.common.util.concurrent.AbstractService;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Starts an HTTP server (if not already started by another instance) and registers this yamcs instance to allow
 * web requests to http://url/instanceName/... 
 * 
 * @author nm
 *
 */
public class YamcsWebService extends AbstractService {
    
    private static final Logger log = LoggerFactory.getLogger(YamcsWebService.class);
    
    private String yamcsInstance;
    private HttpSocketServer server;
    private RestRouter router;
    
    public YamcsWebService(String yamcsInstance) throws ConfigurationException {
        this.yamcsInstance = yamcsInstance;
        StaticFileRequestHandler.init();
        this.server = HttpSocketServer.getInstance();
        
        router = new RestRouter();
        router.registerRestHandler(new ArchiveRequestHandler());
        router.registerRestHandler(new CommandsRequestHandler());
        router.registerRestHandler(new ContainersRequestHandler());
        router.registerRestHandler(new ParametersRequestHandler());
        router.registerRestHandler(new AlarmsRequestHandler());
        router.registerRestHandler(new EventsRequestHandler());
        router.registerRestHandler(HttpSocketServerHandler.clientsRequestHandler);
        router.registerRestHandler(HttpSocketServerHandler.processorsRequestHandler);
        router.registerRestHandler(HttpSocketServerHandler.commandQueuesRequestHandler);
        router.registerRestHandler(new AuthorizationRequestHandler());
        router.registerRestHandler(new SimulationTimeService.SimTimeRequestHandler());
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
    
    public void handleRequest(RestRequest req, String remainingURI) {
        router.handleRequest(req, remainingURI);
    }
    
    /**
     * Forwards to the first maching handler. Extracted in an inner class, mainly to be
     * able to extend AbstractRequestHandler, but could be further improved.
     */
    private class RestRouter extends AbstractRequestHandler {
        
        private List<RestRequestHandler> restHandlers = new ArrayList<>();
        
        public void registerRestHandler(RestRequestHandler restHandler) {
            router.restHandlers.add(restHandler);
        }
        
        /**
         * Matches the request to one of the available handlers.
         * The first matching handler is responsible for further execution.
         * 
         * @param remainingUri
         *            the remaining path without the <tt>/api/:instance</tt> bit
         */
        public void handleRequest(RestRequest req, String remainingURI) {
            if (remainingURI == null) {
                sendError(req.getChannelHandlerContext(), HttpResponseStatus.NOT_FOUND);
                return;
            }
            
            String[] path = remainingURI.split("/", 2);
            if (path.length == 0) {
                sendError(req.getChannelHandlerContext(), HttpResponseStatus.NOT_FOUND);
                return;
            }
            
            int handlerOffset = 3; // Relative to 'full' original path. 0 -> '', 1 -> 'api', 2 -> instance, 3 -> handler 

            String requestPath = req.getPathSegment(handlerOffset);
            for (RestRequestHandler handler : restHandlers) {
                if (handler.getPath().equals(requestPath)) {
                    handler.handleRequestOrError(req, handlerOffset + 1);
                    return;
                }
            }
            
            log.warn("Unknown request received: '{}'", req.getPathSegment(handlerOffset));
            sendError(req.getChannelHandlerContext(), HttpResponseStatus.NOT_FOUND);        
        }   
    }    
}
