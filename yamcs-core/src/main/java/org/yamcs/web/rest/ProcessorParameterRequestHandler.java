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
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.security.Privilege;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to parameters
 */
public class ProcessorParameterRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ProcessorParameterRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "parameters";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MissionDatabaseRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            if (!req.hasPathSegment(pathOffset + 1)) {
                throw new NotFoundException(req);
            } else if ("mget".equals(req.slicePath(pathOffset))) {
                if (req.isGET() || req.isPOST()) {
                    return getParameterValues(req);
                } else {
                    throw new MethodNotAllowedException(req);
                }
            } else if ("mset".equals(req.slicePath(pathOffset))) {
                req.assertPOST();
                return setParameterValues(req, mdb);
            } else {
                // Find out if it's a parameter or not. Support any namespace here. Not just XTCE
                NamedObjectId id = null;
                int i = pathOffset + 1;
                for (; i < req.getPathSegmentCount(); i++) {
                    String namespace = req.slicePath(pathOffset, i);
                    String name = req.getPathSegment(i);
                    id = verifyParameterId(mdb, namespace, name);
                    if (id != null) break;
                }
                if (id == null) throw new NotFoundException(req, "not a valid parameter id");
                Parameter p = mdb.getParameter(id);
                if (p == null) throw new NotFoundException(req, "No parameter for id " + id);
                
                String remainder = null;
                if (++i < req.getPathSegmentCount()) {
                    remainder = req.slicePath(i);
                }
                
                if (remainder == null) {
                    return handleSingleParameter(req, id, p);
                } else if ("history".equals(remainder)) {
                    return handleSingleParameterHistory(req, id, p);
                } else if ("alarms".equals(remainder)) {
                    return handleSingleParameterAlarms(req, id, p);
                } else {
                    throw new NotFoundException(req, "No resource '" + remainder + "' for parameter " + id);
                }
            }
        }
    }
    
    private RestResponse handleSingleParameter(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (req.isGET()) {
            return getParameterValue(req, id, p);
        } else if (req.isPOST() || req.isPUT()) {
            return setSingleParameterValue(req, p);
        } else {
            throw new MethodNotAllowedException(req);
        }
    }
    
    private RestResponse handleSingleParameterHistory(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        return null; // TODO
    }
    
    private RestResponse handleSingleParameterAlarms(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        return null; // TODO
    }
    
    private RestResponse setSingleParameterValue(RestRequest req, Parameter p) throws RestException {
        Value v = req.bodyAsMessage(SchemaYamcs.Value.MERGE).build();
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        
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
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);

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
        
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
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
    
    private static class MyConsumer implements ParameterWithIdConsumer {
        LinkedBlockingQueue<List<ParameterValueWithId>> queue = new LinkedBlockingQueue<>();

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            queue.add(params);
        }
    }
}
