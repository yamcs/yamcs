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
import org.yamcs.web.rest.RestUtils.MatchResult;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SystemParameter;
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
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listParameters(req, null, mdb);
        } else {
            // Find out if it's a parameter or not
            String lastSegment = req.slicePath(-1);
            if (!req.hasPathSegment(pathOffset + 1)) {
                if ("bulk".equals(lastSegment)) {
                    return getBulkParameterInfo(req, mdb);
                } else { // namespace?
                    return listParametersOrError(req, pathOffset);
                }
            } else {
                MatchResult<Parameter> pm = RestUtils.matchParameterName(req, pathOffset);
                if (pm.matches()) { // parameter?
                    return getSingleParameter(req, pm.getRequestedId(), pm.getMatch());
                } else { // namespace?
                    return listParametersOrError(req, pathOffset);
                }
            }
        }
    }
    
    private RestResponse listParametersOrError(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        MatchResult<String> nsm = RestUtils.matchXtceDbNamespace(req, pathOffset);
        if (nsm.matches()) {
            return listParameters(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
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
    private RestResponse listParameters(RestRequest req, String namespace, XtceDb mdb) throws RestException {
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
            Privilege privilege = Privilege.getInstance();
            for (Parameter p : mdb.getParameters()) {
                if (!privilege.hasPrivilege(req.authToken, Type.TM_PARAMETER, p.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(p))
                    continue;
                
                String alias = p.getAlias(namespace);
                if (alias != null) {
                    responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
                }
            }
            for (SystemParameter p : mdb.getSystemParameterDb().getSystemParameters()) {
                // TODO privileges
                String alias = p.getAlias(namespace);
                if (alias != null) {
                    responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY));
                }
            }
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListParametersResponse.WRITE);
    }
    
    private RestResponse getBulkParameterInfo(RestRequest req, XtceDb mdb) throws RestException {
        if (!req.isGET() && !req.isPOST())
            throw new MethodNotAllowedException(req);
        
        BulkGetParameterRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterRequest.MERGE).build();
        BulkGetParameterResponse.Builder responseb = BulkGetParameterResponse.newBuilder();
        for(NamedObjectId id:request.getIdList()) {
            Parameter p = RestUtils.findParameter(mdb, id);
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
}
