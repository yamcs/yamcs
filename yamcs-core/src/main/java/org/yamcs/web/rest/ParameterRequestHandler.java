package org.yamcs.web.rest;

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
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.security.Privilege;
import org.yamcs.xtce.Parameter;

/**
 * Handles incoming requests related to realtime Parameters (get/set).
 * <p>
 * /api/:instance/parameter
 */
public class ParameterRequestHandler extends RestRequestHandler {
    final static Logger log=LoggerFactory.getLogger(ParameterRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "parameter";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");
        
        switch (req.getPathSegment(pathOffset)) {
        case "_get":
            return getParameters(req, processor);
            
        case "_set":
            return setParameters(req, processor);
            
        default:
            // Interpret the remaining path as an absolute parameter name
            String[] pathSegments = req.getPathSegments();
            StringBuilder fqname = new StringBuilder();
            for (int i=pathOffset; i<pathSegments.length; i++) {
                fqname.append("/").append(pathSegments[i]);
            }
            NamedObjectId id = NamedObjectId.newBuilder().setName(fqname.toString()).build();
            
            Parameter p = processor.getXtceDb().getParameter(id);
            if(p==null) {
                log.warn("Invalid parameter requested: {}", id);
                throw new NotFoundException(req);
            }
            if(req.isGET()) {
                ParameterValue pv = getParameterFromCache(id, p, processor);
                if(pv==null) {
                    log.debug("No value found in cache for parameter: {}", id);
                    // TODO this doesn't look like it should return an exception? (FDI)
                    throw new NotFoundException(req);
                } else {
                    return new RestResponse(req, pv, SchemaPvalue.ParameterValue.WRITE);
                }
            } else if(req.isPOST()) {
                Value v = req.bodyAsMessage(SchemaYamcs.Value.MERGE).build();
                setParameter(p, v, processor);
                return new RestResponse(req);
            } else {
                throw new MethodNotAllowedException(req);
            }
        }
    }




    private ParameterValue getParameterFromCache(NamedObjectId id, Parameter p, YProcessor processor) throws BadRequestException {
        //TODO permissions
        ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
        if(!prm.hasParameterCache()) {
            throw new BadRequestException("ParameterCache not activated for this channel");
        }
        org.yamcs.ParameterValue pv = prm.getValueFromCache(p);
        if(pv==null)   return null;
        return pv.toGpb(id);
    }


    /**
     * sets single parameter
     */
    private void setParameter(Parameter p, Value v, YProcessor processor) throws BadRequestException {
        SoftwareParameterManager spm = processor.getParameterRequestManager().getSoftwareParameterManager();
        if(spm==null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this channel");
        }

        try {
            spm.updateParameter(p, v);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * sets multiple parameters parameters
     */
    private RestResponse setParameters(RestRequest req, YProcessor processor) throws RestException {
        ParameterData pdata = req.bodyAsMessage(SchemaPvalue.ParameterData.MERGE).build();

        SoftwareParameterManager spm = processor.getParameterRequestManager().getSoftwareParameterManager();
        if(spm==null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this channel");
        }
        // check permission
        {
            ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
            for(Pvalue.ParameterValue p : pdata.getParameterList())
            {
                try {
                    String parameterName = prm.getParameter(p.getId()).getQualifiedName();
                    if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER_SET, parameterName))
                    {
                        throw  new ForbiddenException("User " + req.authToken + " has no set permission for parameter "
                                + parameterName);
                    }
                } catch (InvalidIdentification invalidIdentification) {
                    throw new BadRequestException("InvalidIdentification: " + invalidIdentification.getMessage());
                }
            }
        }
        try {
            spm.updateParameters(pdata.getParameterList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        return new RestResponse(req);
    }



    /**
     * Gets parameter values
     */
    private RestResponse getParameters(RestRequest req, YProcessor processor) throws RestException {
        BulkGetParameterValueRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterValueRequest.MERGE).build();
        if(request.getIdCount()==0) {
            throw new BadRequestException("Empty parameter list");
        }
        ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
        MyConsumer myConsumer = new MyConsumer();
        ParameterWithIdRequestHelper pwirh = new ParameterWithIdRequestHelper(prm, myConsumer);
        List<NamedObjectId> idList = request.getIdList();
        ParameterData.Builder pdatab = ParameterData.newBuilder();
        try {
            if(request.hasFromCache()) {
                if(!prm.hasParameterCache()) {
                    throw new BadRequestException("ParameterCache not activated for this processor");
                }
                List<ParameterValueWithId> l;
                l = pwirh.getValuesFromCache(idList, req.authToken);
                for(ParameterValueWithId pvwi: l) {
                    pdatab.addParameter(pvwi.toGbpParameterValue());
                }
            } else {

                long timeout = getTimeout(request);
                int reqId = pwirh.addRequest(idList, req.authToken);
                long t0 = System.currentTimeMillis();
                long t1;
                while(true) {
                    t1 = System.currentTimeMillis();
                    long remaining = timeout - (t1-t0);
                    List<ParameterValueWithId> l = myConsumer.queue.poll(remaining, TimeUnit.MILLISECONDS);
                    if(l==null) break;

                    for(ParameterValueWithId pvwi: l) {
                        pdatab.addParameter(pvwi.toGbpParameterValue());
                    }
                    //TODO: this may not be correct: if we get a parameter multiple times, we stop here before receiving all parameters
                    if(pdatab.getParameterCount() == idList.size()) break;
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

        return new RestResponse(req, pdatab.build(), SchemaPvalue.ParameterData.WRITE);
    }


    private long getTimeout(BulkGetParameterValueRequest request) throws BadRequestException {
        long timeout = 10000;
        if(request.hasTimeout()) {
            timeout = request.getTimeout();
            if(timeout>60000) {
                throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
            }
        }
        return timeout;
    }


    class MyConsumer implements ParameterWithIdConsumer {
        LinkedBlockingQueue<List<ParameterValueWithId>> queue = new LinkedBlockingQueue<>();

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            queue.add(params);
        }
    }
}
