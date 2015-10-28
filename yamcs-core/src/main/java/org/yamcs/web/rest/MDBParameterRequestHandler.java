package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Rest.BulkGetParameterRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterResponse;
import org.yamcs.protobuf.Rest.ListParametersResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to parameter info from the MDB
 */
public class MDBParameterRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBParameterRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "parameters";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MissionDatabaseRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listAvailableParameters(req, null, mdb);
        } else {
            // Find out if it's a parameter or not. Support any namespace here. Not just XTCE
            String lastSegment = req.slicePath(-1);
            if (!req.hasPathSegment(pathOffset + 1)) {
                if ("bulk".equals(lastSegment)) {
                    return getBulkParameterInfo(req, mdb);
                } else { // Assume namespace
                    return listAvailableParameters(req, lastSegment, mdb);
                }
            } else {
                String namespace = req.slicePath(pathOffset, -1);
                NamedObjectId id = verifyParameterId(mdb, namespace, lastSegment);
                if (id != null) {
                    Parameter p = mdb.getParameter(id);
                    return getSingleParameter(req, id, p);
                }

                // Assume namespace
                return listAvailableParameters(req, namespace + "/" + lastSegment, mdb);                
            }
        }
    }
    
    private RestResponse getSingleParameter(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid parameter name specified "+id);
        }
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.FULL);
        return new RestResponse(req, pinfo, SchemaMdb.ParameterInfo.WRITE);
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listAvailableParameters(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder();
        if (namespace == null) {
            for (Parameter p : mdb.getParameters()) {
                if (matcher != null && !matcher.matches(p)) continue;
                responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
            }
        } else {
            String rootedNamespace = "/" + namespace;
            Privilege privilege = Privilege.getInstance();
            for (Parameter p : mdb.getParameters()) {
                if (!privilege.hasPrivilege(req.authToken, Type.TM_PARAMETER, p.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(p))
                    continue;
                
                String alias = p.getAlias(namespace);
                if (alias != null) {
                    responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
                } else {
                    // Slash is not added to the URL so it makes it a bit more difficult
                    // to test for both XTCE names and other names. So just test with slash too
                    alias = p.getAlias(rootedNamespace);
                    if (alias != null) {
                        responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
                    }
                }
            }
        }
        
        // There's no such thing as a list of 'namespaces' within the MDB, therefore it
        // could happen that we arrive here but that the user intended to search for a single
        // parameter rather than a list. So... return a 404 if we didn't find any match.
        if (matcher == null && (responseb.getParameterList() == null || responseb.getParameterList().isEmpty())) {
            throw new NotFoundException(req);
        } else {
            return new RestResponse(req, responseb.build(), SchemaRest.ListParametersResponse.WRITE);
        }
    }
    
    private RestResponse getBulkParameterInfo(RestRequest req, XtceDb mdb) throws RestException {
        if (!req.isGET() && !req.isPOST())
            throw new MethodNotAllowedException(req);
        
        BulkGetParameterRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterRequest.MERGE).build();
        BulkGetParameterResponse.Builder responseb = BulkGetParameterResponse.newBuilder();
        for(NamedObjectId id:request.getIdList()) {
            Parameter p = mdb.getParameter(id);
            if(p==null) {
                throw new BadRequestException("Invalid parameter name specified "+id);
            }
            if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists", p.getQualifiedName());
                continue;
            }
            
            BulkGetParameterResponse.GetParameterResponse.Builder response = BulkGetParameterResponse.GetParameterResponse.newBuilder();
            response.setId(id);
            String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
            responseb.addResponse(response);
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.BulkGetParameterResponse.WRITE);
    }
    
    private NamedObjectId verifyParameterId(XtceDb mdb, String namespace, String name) {
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        if (mdb.getParameter(id) != null)
            return id;
        
        String rootedNamespace = "/" + namespace;
        id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(name).build();
        if (mdb.getParameter(id) != null)
            return id;
        
        return null;
    }
}
