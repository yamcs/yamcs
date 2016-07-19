package org.yamcs.web.rest.processor;

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
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.EditAlarmRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.ValueUtility;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;

public class ProcessorParameterRestHandler extends RestHandler {
    
    private final static Logger log = LoggerFactory.getLogger(ProcessorParameterRestHandler.class);
    
    @Route(path = "/api/processors/:instance/:processor/parameters/:name*/alarms/:seqnum", method = { "PATCH", "PUT", "POST" })
    public ChannelFuture patchParameterAlarm(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        AlarmServer alarmServer = verifyAlarmServer(processor);
        
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        int seqNum = req.getIntegerRouteParam("seqnum");
        
        String state = null;
        String comment = null;
        EditAlarmRequest request = req.bodyAsMessage(SchemaRest.EditAlarmRequest.MERGE).build();
        if (request.hasState()) state = request.getState();
        if (request.hasComment()) comment = request.getComment();
        
        // URI can override body
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        if (req.hasQueryParameter("comment")) comment = req.getQueryParameter("comment");
        
        switch (state.toLowerCase()) {
        case "acknowledged":
            try {
                // TODO permissions on AlarmServer
                alarmServer.acknowledge(p, seqNum, req.getUsername(), processor.getCurrentTime(), comment);
                return sendOK(req);
            } catch (CouldNotAcknowledgeAlarmException e) {
                log.debug("Did not acknowledge alarm " + seqNum + ". " + e.getMessage());
                throw new BadRequestException(e.getMessage());
            }
        default:
            throw new BadRequestException("Unsupported state '" + state + "'");
        }
    }
    
    @Route(path = "/api/processors/:instance/:processor/parameters/:name*", method = { "PUT", "POST" })
    public ChannelFuture setSingleParameterValue(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        SoftwareParameterManager mgr = verifySoftwareParameterManager(processor);
        
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        
        Value v = ValueUtility.fromGpb(req.bodyAsMessage(SchemaYamcs.Value.MERGE).build());
        try {
            mgr.updateParameter(p, v);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        return sendOK(req);
    }
    
    @Route(path = "/api/processors/:instance/:processor/parameters/mset", method = { "POST", "PUT" }, priority=true)
    public ChannelFuture setParameterValues(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        SoftwareParameterManager mgr = verifySoftwareParameterManager(processor);
        
        BulkSetParameterValueRequest request = req.bodyAsMessage(SchemaRest.BulkSetParameterValueRequest.MERGE).build();

        // check permission
        ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
        for(SetParameterValueRequest r : request.getRequestList()) {
            try {
                String parameterName = prm.getParameter(r.getId()).getQualifiedName();
                if(!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.TM_PARAMETER_SET, parameterName)) {
                    throw new ForbiddenException("User " + req.getAuthToken() + " has no 'set' permission for parameter "
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
            mgr.updateParameters(pvals);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        return sendOK(req);
    }
    
    @Route(path = "/api/processors/:instance/:processor/parameters/:name*", method = "GET")
    public ChannelFuture getParameterValue(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        
        if (!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}", p.getQualifiedName(), req.getAuthToken());
            throw new BadRequestException("Invalid parameter name specified");
        }
        long timeout = 10000;
        boolean fromCache = true;
        if (req.hasQueryParameter("timeout")) timeout = req.getQueryParameterAsLong("timeout");
        if (req.hasQueryParameter("fromCache")) fromCache = req.getQueryParameterAsBoolean("fromCache");
        
        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        List<NamedObjectId> ids = Arrays.asList(id);
        List<ParameterValue> pvals = doGetParameterValues(processor, req.getAuthToken(), ids, fromCache, timeout);

        ParameterValue pval;
        if (pvals.isEmpty()) {
            pval = ParameterValue.newBuilder().setId(id).build();
        } else {
            pval = pvals.get(0);
        }
            
        return sendOK(req, pval, SchemaPvalue.ParameterValue.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor/parameters/mget", method = {"GET", "POST"}, priority=true)
    public ChannelFuture getParameterValues(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        BulkGetParameterValueRequest request = req.bodyAsMessage(SchemaRest.BulkGetParameterValueRequest.MERGE).build();
        if (request.getIdCount() == 0) {
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
        List<ParameterValue> pvals = doGetParameterValues(processor, req.getAuthToken(), ids, fromCache, timeout);

        BulkGetParameterValueResponse.Builder responseb = BulkGetParameterValueResponse.newBuilder();
        responseb.addAllValue(pvals);
        return sendOK(req, responseb.build(), SchemaRest.BulkGetParameterValueResponse.WRITE);
    }
    
    private List<ParameterValue> doGetParameterValues(YProcessor processor, AuthenticationToken authToken, List<NamedObjectId> ids, boolean fromCache, long timeout) throws HttpException {
        if (timeout > 60000) {
            throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
        }

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
                l = pwirh.getValuesFromCache(ids, authToken);
                for(ParameterValueWithId pvwi: l) {
                    pvals.add(pvwi.toGbpParameterValue());
                }
            } else {

                int reqId = pwirh.addRequest(ids, authToken);
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
    
    private static class MyConsumer implements ParameterWithIdConsumer {
        LinkedBlockingQueue<List<ParameterValueWithId>> queue = new LinkedBlockingQueue<>();

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            queue.add(params);
        }
    }
    
    private SoftwareParameterManager verifySoftwareParameterManager(YProcessor processor) throws BadRequestException {
        SoftwareParameterManager mgr = processor.getParameterRequestManager().getSoftwareParameterManager();
        if (mgr == null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this processor");
        } else {
            return mgr;
        }
    }
}
