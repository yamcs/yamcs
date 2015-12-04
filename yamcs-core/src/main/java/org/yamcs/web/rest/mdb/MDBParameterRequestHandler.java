package org.yamcs.web.rest.mdb;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Rest.BulkGetParameterInfoRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterInfoResponse;
import org.yamcs.protobuf.Rest.ListParameterInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.MethodNotAllowedException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.XtceToGpbAssembler;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to parameter info from the MDB
 */
public class MDBParameterRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBParameterRequestHandler.class.getName());
    
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
                MatchResult<Parameter> pm = MissionDatabaseHelper.matchParameterName(req, pathOffset);
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
        MatchResult<String> nsm = MissionDatabaseHelper.matchXtceDbNamespace(req, pathOffset, true);
        if (nsm.matches()) {
            return listParameters(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse getSingleParameter(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.getAuthToken());
            throw new BadRequestException("Invalid parameter name specified "+id);
        }
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.FULL, req.getOptions());
        return new RestResponse(req, pinfo, SchemaMdb.ParameterInfo.WRITE);
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listParameters(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        // Support both type[]=float&type[]=integer and type=float,integer
        Set<String> types = new HashSet<>();
        if (req.hasQueryParameter("type")) {
            for (String type : req.getQueryParameterList("type")) {
                for (String t : type.split(",")) {
                    if (!"all".equalsIgnoreCase(t)) {
                        types.add(t.toLowerCase());
                    }
                }
            }
        }
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListParameterInfoResponse.Builder responseb = ListParameterInfoResponse.newBuilder();
        if (namespace == null) {
            for (Parameter p : mdb.getParameters()) {
                if (matcher != null && !matcher.matches(p)) continue;
                if (parameterTypeMatches(p, types)) {
                    responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        } else {
            Privilege privilege = Privilege.getInstance();
            for (Parameter p : mdb.getParameters()) {
                if (!privilege.hasPrivilege(req.getAuthToken(), Type.TM_PARAMETER, p.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(p))
                    continue;
                
                String alias = p.getAlias(namespace);
                if (alias != null || (recurse && p.getQualifiedName().startsWith(namespace))) {
                    if (parameterTypeMatches(p, types)) {
                        responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                    }
                }
            }
            for (SystemParameter p : mdb.getSystemParameterDb().getSystemParameters()) {
                if (!privilege.hasPrivilege(req.getAuthToken(), Type.TM_PARAMETER, p.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(p))
                    continue;
                
                String alias = p.getAlias(namespace);
                if (alias != null || (recurse && p.getQualifiedName().startsWith(namespace))) {
                    if (parameterTypeMatches(p, types)) {
                        responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                    }
                }
            }
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListParameterInfoResponse.WRITE);
    }
    
    private boolean parameterTypeMatches(Parameter p, Set<String> types) {
        if (types.isEmpty()) return true;
        return p.getParameterType() != null
                && types.contains(p.getParameterType().getTypeAsString());
    }
    
    private RestResponse getBulkParameterInfo(RestRequest req, XtceDb mdb) throws RestException {
        if (!req.isGET() && !req.isPOST())
            throw new MethodNotAllowedException(req);
        
        BulkGetParameterInfoRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterInfoRequest.MERGE).build();
        BulkGetParameterInfoResponse.Builder responseb = BulkGetParameterInfoResponse.newBuilder();
        for(NamedObjectId id:request.getIdList()) {
            Parameter p = MissionDatabaseHelper.findParameter(mdb, id);
            if(p==null) {
                throw new BadRequestException("Invalid parameter name specified "+id);
            }
            if(!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists", p.getQualifiedName());
                continue;
            }
            
            BulkGetParameterInfoResponse.GetParameterInfoResponse.Builder response = BulkGetParameterInfoResponse.GetParameterInfoResponse.newBuilder();
            response.setId(id);
            String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            responseb.addResponse(response);
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.BulkGetParameterInfoResponse.WRITE);
    }
}
