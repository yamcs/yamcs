package org.yamcs.web.rest.mdb;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.HistoryInfo;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.SpaceSystemInfo;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to parameters
 */
public class MDBRequestHandler extends RestRequestHandler {
    
    final static Logger log = LoggerFactory.getLogger(MDBRequestHandler.class.getName());

    public static final String CTX_MDB = "mdb";
    
    private static MDBParameterRequestHandler parameterHandler = new MDBParameterRequestHandler();    
    private static MDBContainerRequestHandler containerHandler = new MDBContainerRequestHandler();
    private static MDBCommandRequestHandler commandHandler = new MDBCommandRequestHandler();
    private static MDBAlgorithmRequestHandler algorithmHandler = new MDBAlgorithmRequestHandler();
    
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
            req.addToContext(RestRequest.CTX_INSTANCE, instance);
            
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            req.addToContext(CTX_MDB, mdb);
            
            pathOffset += 1; // jump past instance
            if (!req.hasPathSegment(pathOffset)) {
                return getMissionDatabase(req, instance, mdb);
            } else {
                switch (req.getPathSegment(pathOffset)) {
                case "parameters":
                    return parameterHandler.handleRequest(req, pathOffset + 1);
                case "containers":
                    return containerHandler.handleRequest(req, pathOffset + 1);
                case "commands":
                    return commandHandler.handleRequest(req, pathOffset + 1);
                case "algorithms":
                    return algorithmHandler.handleRequest(req, pathOffset + 1);
                default:
                    throw new NotFoundException(req);
                }
            }
        }
    }
    
    private RestResponse getMissionDatabase(RestRequest req, String instance, XtceDb mdb) throws RestException {
        MissionDatabase converted = toMissionDatabase(req, instance, mdb);
        return new RestResponse(req, converted, SchemaYamcsManagement.MissionDatabase.WRITE);
    }
    
    public static MissionDatabase toMissionDatabase(RestRequest req, String instance, XtceDb mdb) {
        YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
        MissionDatabase.Builder b = MissionDatabase.newBuilder(yamcsInstance.getMissionDatabase());
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String apiUrl = req.getApiURL();
            b.setUrl(apiUrl + "/mdb/" + instance);
            b.setParametersUrl(b.getUrl() + "/parameters{/namespace}{/name}");
            b.setContainersUrl(b.getUrl() + "/containers{/namespace}{/name}");
            b.setCommandsUrl(b.getUrl() + "/commands{/namespace}{/name}");
            b.setAlgorithmsUrl(b.getUrl() + "/algorithms{/namespace}{/name}");
        }
        
        SpaceSystem ss = mdb.getRootSpaceSystem();
        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSpaceSystem(toSpaceSystemInfo(req, instance, sub));
        }        
        return b.build();
    }
    
    private static SpaceSystemInfo toSpaceSystemInfo(RestRequest req, String instance, SpaceSystem ss) {
        SpaceSystemInfo.Builder b = SpaceSystemInfo.newBuilder();
        b.setName(ss.getName());
        b.setQualifiedName(ss.getQualifiedName());
        if (ss.getShortDescription() != null) {
            b.setShortDescription(ss.getShortDescription());
        }
        if (ss.getLongDescription() != null) {
            b.setLongDescription(ss.getLongDescription());
        }
        Header h = ss.getHeader();
        if (h != null) {
            if (h.getVersion() != null) {
                b.setVersion(h.getVersion());
            }
            
            History[] sortedHistory = h.getHistoryList().toArray(new History[] {});
            Arrays.sort(sortedHistory);
            for (History history : sortedHistory) {
                HistoryInfo.Builder historyb = HistoryInfo.newBuilder();
                if (history.getVersion() != null) historyb.setVersion(history.getVersion());
                if (history.getDate() != null) historyb.setDate(history.getDate());
                if (history.getMessage() != null) historyb.setMessage(history.getMessage());
                b.addHistory(historyb);
            }
        }
        b.setParameterCount(ss.getParameters().size());
        b.setContainerCount(ss.getSequenceContainers().size());
        b.setCommandCount(ss.getMetaCommands().size());
        b.setAlgorithmCount(ss.getAlgorithms().size());
        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSub(toSpaceSystemInfo(req, instance, sub));
        }
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String url = req.getApiURL() + "/mdb/" + instance;
            b.setParametersUrl(url + "/parameters" + ss.getQualifiedName());
            b.setContainersUrl(url + "/containers" + ss.getQualifiedName());
            b.setCommandsUrl(url + "/commands" + ss.getQualifiedName());
            b.setAlgorithmsUrl(url + "/algorithms" + ss.getQualifiedName());
        }
        return b.build();
    }
}
