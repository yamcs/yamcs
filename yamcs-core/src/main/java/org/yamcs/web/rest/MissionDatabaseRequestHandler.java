package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to parameters
 */
public class MissionDatabaseRequestHandler extends RestRequestHandler {
    
    final static Logger log = LoggerFactory.getLogger(MissionDatabaseRequestHandler.class.getName());

    public static final String CTX_MDB = "mdb";
    
    private static MDBParameterRequestHandler parameterHandler = new MDBParameterRequestHandler();    
    private static MDBContainerRequestHandler containerHandler = new MDBContainerRequestHandler();
    private static MDBCommandRequestHandler commandHandler = new MDBCommandRequestHandler();
    
    @Override
    public String getPath() {
        return "mdb";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            String instance = req.getPathSegment(pathOffset);
            YamcsServer yamcsInstance = YamcsServer.getInstance(instance);
            if (yamcsInstance == null) {
                throw new NotFoundException(req);
            }
            req.addToContext(RestRequest.CTX_INSTANCE, yamcsInstance);
            
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            req.addToContext(CTX_MDB, mdb);
            
            pathOffset += 1; // jump past instance
            switch (req.getPathSegment(pathOffset)) {
            case "parameters":
                return parameterHandler.handleRequest(req, pathOffset + 1);
            case "containers":
                return containerHandler.handleRequest(req, pathOffset + 1);
            case "commands":
                return commandHandler.handleRequest(req, pathOffset + 1);
            default:
                throw new NotFoundException(req);
            }
        }
    }
    
    public static MissionDatabase toMissionDatabase(RestRequest req, String instance) {
        YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
        MissionDatabase.Builder b = MissionDatabase.newBuilder(yamcsInstance.getMissionDatabase());
        String apiUrl = req.getApiURL();
        b.setUrl(apiUrl + "/mdb/" + instance);
        b.setParametersUrl(b.getUrl() + "/parameters{/namespace}{/name}");
        b.setContainersUrl(b.getUrl() + "/containers{/namespace}{/name}");
        b.setCommandsUrl(b.getUrl() + "/commands{/namespace}{/name}");
        return b.build();
    }
}
