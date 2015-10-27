package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterResponse;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.ListParametersResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to parameters
 * <p>
 * /api/:instance/parameters
 */
public class ParametersRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ParametersRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "parameters";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = loadMdb(req.getYamcsInstance());
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
            } else if ("value".equals(lastSegment)) {
                String namespace = req.slicePath(pathOffset, -2);
                String name = req.slicePath(-2, -1);
                NamedObjectId id = verifyParameterId(mdb, namespace, name);
                if (id != null) {
                    Parameter p = mdb.getParameter(id);
                    if (req.isGET()) {
                        return getParameterValue(req, id, p);
                    } else if (req.isPOST()) {
                        return setSingleParameterValue(req, p);
                    } else {
                        throw new MethodNotAllowedException(req);
                    }
                } else {
                    throw new NotFoundException(req);
                }
            } else if ("values/bulk".equals(req.slicePath(pathOffset))) {
                if (req.isGET() || req.isPOST()) {
                    return getParameterValues(req);
                } else {
                    throw new MethodNotAllowedException(req);
                }
            } else if ("values/bulkset".equals(req.slicePath(pathOffset))) {
                req.assertPOST();
                return setParameterValues(req, mdb);
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
    
    private RestResponse setSingleParameterValue(RestRequest req, Parameter p) throws RestException {
        Value v = req.bodyAsMessage(SchemaYamcs.Value.MERGE).build();
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");
        SoftwareParameterManager spm = processor.getParameterRequestManager().getSoftwareParameterManager();
        if(spm==null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this processor");
        }

        try {
            spm.updateParameter(p, v);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        return new RestResponse(req);
    }
    
    private RestResponse setParameterValues(RestRequest req, XtceDb mdb) throws RestException {
        BulkSetParameterValueRequest request = req.bodyAsMessage(SchemaRest.BulkSetParameterValueRequest.MERGE).build();
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");

        SoftwareParameterManager spm = processor.getParameterRequestManager().getSoftwareParameterManager();
        if(spm==null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this channel");
        }
        // check permission
        ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
        for(SetParameterValueRequest r : request.getRequestList()) {
            try {
                String parameterName = prm.getParameter(r.getId()).getQualifiedName();
                if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER_SET, parameterName)) {
                    throw new ForbiddenException("User " + req.authToken + " has no set permission for parameter "
                            + parameterName);
                }
            } catch (InvalidIdentification e) {
                throw new BadRequestException("InvalidIdentification: " + e.getMessage());
            }
        }
        
        // Yamcs uses ParameterValue, so map to that structure
        List<ParameterValue> pvals = new ArrayList<>();
        for (SetParameterValueRequest r : request.getRequestList()) {
            ParameterValue.Builder pvalb = ParameterValue.newBuilder();
            pvalb.setId(r.getId());
            pvalb.setEngValue(r.getValue());
            pvals.add(pvalb.build());
        }
        try {
            spm.updateParameters(pvals);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        return new RestResponse(req);
    }
    
    private RestResponse getSingleParameter(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid parameter name specified "+id);
        }
        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(p, req.getInstanceURL(), DetailLevel.FULL);
        return new RestResponse(req, pinfo, SchemaMdb.ParameterInfo.WRITE);
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listAvailableParameters(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder();
        if (namespace == null) {
            for (Parameter p : mdb.getParameters()) {
                if (matcher != null && !matcher.matches(p)) continue;
                responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, req.getInstanceURL(), DetailLevel.SUMMARY));
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
                    responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, req.getInstanceURL(), DetailLevel.SUMMARY));
                } else {
                    // Slash is not added to the URL so it makes it a bit more difficult
                    // to test for both XTCE names and other names. So just test with slash too
                    alias = p.getAlias(rootedNamespace);
                    if (alias != null) {
                        responseb.addParameter(XtceToGpbAssembler.toParameterInfo(p, req.getInstanceURL(), DetailLevel.SUMMARY));
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
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, req.getInstanceURL(), DetailLevel.SUMMARY));
            responseb.addResponse(response);
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.BulkGetParameterResponse.WRITE);
    }
    
    private RestResponse getParameterValue(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid parameter name specified");
        }
        long timeout = 10000;
        boolean fromCache = true;
        if (req.hasQueryParameter("timeout")) timeout = req.getQueryParameterAsLong("timeout");
        if (req.hasQueryParameter("fromCache")) fromCache = req.getQueryParameterAsBoolean("fromCache");
        
        List<NamedObjectId> ids = Arrays.asList(id);
        List<ParameterValue> pvals = doGetParameterValues(req, ids, fromCache, timeout);

        ParameterValue pval;
        if (pvals.isEmpty()) {
            pval = ParameterValue.newBuilder().setId(id).build();
        } else {
            pval = pvals.get(0);
        }
            
        return new RestResponse(req, pval, SchemaPvalue.ParameterValue.WRITE);
    }
    
    private RestResponse getParameterValues(RestRequest req) throws RestException {
        BulkGetParameterValueRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterValueRequest.MERGE).build();
        if(request.getIdCount()==0) {
            throw new BadRequestException("Empty parameter list");
        }

        long timeout = 10000;
        boolean fromCache = true;
        
        // Consider body params first
        if (request.hasTimeout()) timeout = request.getTimeout();
        if (request.hasFromCache()) fromCache = request.getFromCache();
            
        // URI params override body
        if (req.hasQueryParameter("timeout")) timeout = req.getQueryParameterAsLong("timeout");
        if (req.hasQueryParameter("fromCache")) fromCache = req.getQueryParameterAsBoolean("fromCache");
        
        List<NamedObjectId> ids = request.getIdList();
        List<ParameterValue> pvals = doGetParameterValues(req, ids, fromCache, timeout);

        BulkGetParameterValueResponse.Builder responseb = BulkGetParameterValueResponse.newBuilder();
        responseb.addAllValue(pvals);
        return new RestResponse(req, responseb.build(), SchemaRest.BulkGetParameterValueResponse.WRITE);
    }
    
    private List<ParameterValue> doGetParameterValues(RestRequest req, List<NamedObjectId> ids, boolean fromCache, long timeout) throws RestException {
        if (timeout > 60000) {
            throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
        }
        
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");
        ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
        MyConsumer myConsumer = new MyConsumer();
        ParameterWithIdRequestHelper pwirh = new ParameterWithIdRequestHelper(prm, myConsumer);
        List<ParameterValue> pvals = new ArrayList<>();
        try {
            if(fromCache) {
                if(!prm.hasParameterCache()) {
                    throw new BadRequestException("ParameterCache not activated for this processor");
                }
                List<ParameterValueWithId> l;
                l = pwirh.getValuesFromCache(ids, req.authToken);
                for(ParameterValueWithId pvwi: l) {
                    pvals.add(pvwi.toGbpParameterValue());
                }
            } else {

                int reqId = pwirh.addRequest(ids, req.authToken);
                long t0 = System.currentTimeMillis();
                long t1;
                while(true) {
                    t1 = System.currentTimeMillis();
                    long remaining = timeout - (t1-t0);
                    List<ParameterValueWithId> l = myConsumer.queue.poll(remaining, TimeUnit.MILLISECONDS);
                    if(l==null) break;

                    for(ParameterValueWithId pvwi: l) {
                        pvals.add(pvwi.toGbpParameterValue());
                    }
                    //TODO: this may not be correct: if we get a parameter multiple times, we stop here before receiving all parameters
                    if(pvals.size() == ids.size()) break;
                } 
                pwirh.removeRequest(reqId);
            }
        }  catch (InvalidIdentification e) {
            //TODO - send the invalid parameters in a parsable form
            throw new BadRequestException("Invalid parameters: "+e.invalidParameters.toString());
        } catch (InterruptedException e) {
            throw new InternalServerErrorException("Interrupted while waiting for parameters");
        } catch (NoPermissionException e) {
            throw new ForbiddenException(e.getMessage(), e);
        }
        
        return pvals;
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
    
    static class MyConsumer implements ParameterWithIdConsumer {
        LinkedBlockingQueue<List<ParameterValueWithId>> queue = new LinkedBlockingQueue<>();

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            queue.add(params);
        }
    }
}
