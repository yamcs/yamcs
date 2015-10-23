package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Parameters.ListParametersRequest;
import org.yamcs.protobuf.Parameters.ListParametersResponse;
import org.yamcs.protobuf.Parameters.ParameterInfo;
import org.yamcs.protobuf.SchemaParameters;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.DumpRawMdbResponse;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to the Mission Database
 * <p>
 * /api/:instance/mdb
 */
public class MdbRequestHandler extends RestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(MdbRequestHandler.class);
    
    @Override
    public String getPath() {
        return "mdb";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        //because it is difficult to send GET requests with body from some clients (including jquery), we allow POST requests here.
        //       req.assertGET();
        switch (req.getPathSegment(pathOffset)) {
        case "dump":
            return dumpRawMdb(req);
            
        case "parameterInfo":
            if (req.getQueryParameters().containsKey("name")) { //one parameter specified in the URL, we return one RestParameterInfo
                String name = req.getQueryParameters().get("name").get(0);                
                NamedObjectId.Builder noib = NamedObjectId.newBuilder();
                noib.setName(name);
                if (req.getQueryParameters().containsKey("namespace")) {
                    noib.setNamespace(req.getQueryParameters().get("namespace").get(0));
                }
                NamedObjectId id = noib.build();
                XtceDb xtceDb = loadMdb(req.yamcsInstance);
                Parameter p = xtceDb.getParameter(id);
                if(p==null) {
                    log.warn("Invalid parameter name specified: {}", id);
                    throw new BadRequestException("Invalid parameter name specified "+id);
                }
                if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                    log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
                    throw new BadRequestException("Invalid parameter name specified "+id);
                }
                ParameterInfo pinfo = ParametersRequestHandler.toParameterInfo(id, p);
                return new RestResponse(req, pinfo, SchemaParameters.ParameterInfo.WRITE);
            } else { //possible multiple parameter requested, return GetParameterInfoResponse
                return getParameterInfo(req);
            }
            
        default:
            throw new NotFoundException(req);
        }
    }

    private RestResponse dumpRawMdb(RestRequest req) throws RestException {
        DumpRawMdbResponse.Builder responseb = DumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb xtceDb = loadMdb(req.yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(xtceDb);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not serialize MDB", e);
        }
        responseb.setRawMdb(bout.toByteString());
        return new RestResponse(req, responseb.build(), SchemaYamcs.DumpRawMdbResponse.WRITE);
    }
    
    private RestResponse getParameterInfo(RestRequest req) throws RestException {
        XtceDb xtceDb = loadMdb(req.yamcsInstance);
        
        ListParametersRequest request = req.bodyAsMessage(SchemaParameters.ListParametersRequest.MERGE).build();
        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder();
        for(NamedObjectId id:request.getListList()) {
            Parameter p = xtceDb.getParameter(id);
            if(p==null) {
                throw new BadRequestException("Invalid parameter name specified "+id);
            }
            if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists", p.getQualifiedName());
                continue;
            }
            responseb.addParameter(ParametersRequestHandler.toParameterInfo(id, p));
        }
        
        return new RestResponse(req, responseb.build(), SchemaParameters.ListParametersResponse.WRITE);
    }

    private XtceDb loadMdb(String yamcsInstance) throws RestException {
        try {
            return XtceDbFactory.getInstance(yamcsInstance);
        } catch(ConfigurationException e) {
            log.error("Could not get MDB for instance '" + yamcsInstance + "'", e);
            throw new InternalServerErrorException("Could not get MDB for instance '" + yamcsInstance + "'", e);
        }
    }
}
