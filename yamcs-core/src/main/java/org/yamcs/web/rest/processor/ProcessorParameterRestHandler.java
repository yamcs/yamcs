package org.yamcs.web.rest.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.SoftwareParameterManagerIf;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.EditAlarmRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.ValueUtility;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class ProcessorParameterRestHandler extends RestHandler {

    private final static Logger log = LoggerFactory.getLogger(ProcessorParameterRestHandler.class);

    @Route(path = "/api/processors/:instance/:processor/parameters/:name*/alarms/:seqnum", method = { "PATCH", "PUT",
            "POST" })
    public void patchParameterAlarm(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        AlarmServer alarmServer = verifyAlarmServer(processor);

        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        int seqNum = req.getIntegerRouteParam("seqnum");

        String state = null;
        String comment = null;
        EditAlarmRequest request = req.bodyAsMessage(EditAlarmRequest.newBuilder()).build();
        if (request.hasState()) {
            state = request.getState();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        // URI can override body
        if (req.hasQueryParameter("state")) {
            state = req.getQueryParameter("state");
        }
        if (req.hasQueryParameter("comment")) {
            comment = req.getQueryParameter("comment");
        }
        if (state == null) {
            throw new BadRequestException("No state specified");
        }

        switch (state.toLowerCase()) {
        case "acknowledged":
            try {
                // TODO permissions on AlarmServer
                String username = req.getUser().getUsername();
                alarmServer.acknowledge(p, seqNum, username, processor.getCurrentTime(), comment);
                completeOK(req);
            } catch (CouldNotAcknowledgeAlarmException e) {
                log.debug("Did not acknowledge alarm {}.{}", seqNum, e.getMessage());
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unsupported state '" + state + "'");
        }
    }

    @Route(path = "/api/processors/:instance/:processor/parameters/:name*", method = { "PUT", "POST" })
    public void setSingleParameterValue(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));

        SoftwareParameterManagerIf mgr = verifySoftwareParameterManager(processor, p.getDataSource());
      
        Value v = ValueUtility.fromGpb(req.bodyAsMessage(org.yamcs.protobuf.Yamcs.Value.newBuilder()).build());
        try {
            mgr.updateParameter(p, v);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        completeOK(req);
    }

    @Route(path = "/api/processors/:instance/:processor/parameters/mset", method = { "POST", "PUT" }, priority = true)
    public void setParameterValues(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        BulkSetParameterValueRequest request = req.bodyAsMessage(BulkSetParameterValueRequest.newBuilder()).build();

        // check permission
        ParameterRequestManager prm = processor.getParameterRequestManager();
        Map<DataSource, List<org.yamcs.parameter.ParameterValue>> pvmap = new HashMap<>();
        for (SetParameterValueRequest r : request.getRequestList()) {
            try {
                Parameter p = prm.getParameter(r.getId());
                checkObjectPrivileges(req, ObjectPrivilegeType.WriteParameter, p.getQualifiedName());
                org.yamcs.parameter.ParameterValue pv = new org.yamcs.parameter.ParameterValue(p);
                pv.setEngineeringValue(ValueUtility.fromGpb(r.getValue()));
                List<org.yamcs.parameter.ParameterValue> l = pvmap.computeIfAbsent(p.getDataSource(), k -> new ArrayList<>());
                l.add(pv);
            } catch (InvalidIdentification e) {
                throw new BadRequestException("InvalidIdentification: " + e.getMessage());
            }
        }
        for(Map.Entry<DataSource, List<org.yamcs.parameter.ParameterValue>> me: pvmap.entrySet()) {
            List<org.yamcs.parameter.ParameterValue> l = me.getValue();
            DataSource ds = me.getKey();
            SoftwareParameterManagerIf mgr = verifySoftwareParameterManager(processor, ds);

            try {
                mgr.updateParameters(l);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
        completeOK(req);
    }

    @Route(path = "/api/processors/:instance/:processor/parameters/:name*", method = "GET")
    public void getParameterValue(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));

        checkObjectPrivileges(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
        long timeout = 10000;
        boolean fromCache = true;
        if (req.hasQueryParameter("timeout")) {
            timeout = req.getQueryParameterAsLong("timeout");
        }
        if (req.hasQueryParameter("fromCache")) {
            fromCache = req.getQueryParameterAsBoolean("fromCache");
        }

        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        List<NamedObjectId> ids = Arrays.asList(id);
        List<ParameterValue> pvals = doGetParameterValues(processor, req.getUser(), ids, fromCache, timeout);

        ParameterValue pval;
        if (pvals.isEmpty()) {
            pval = ParameterValue.newBuilder().setId(id).build();
        } else {
            pval = pvals.get(0);
        }

        completeOK(req, pval);
    }

    @Route(path = "/api/processors/:instance/:processor/parameters/mget", method = { "GET", "POST" }, priority = true)
    public void getParameterValues(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        BulkGetParameterValueRequest request = req.bodyAsMessage(BulkGetParameterValueRequest.newBuilder()).build();
        if (request.getIdCount() == 0) {
            throw new BadRequestException("Empty parameter list");
        }

        long timeout = 10000;
        boolean fromCache = true;

        // Consider body params first
        if (request.hasTimeout()) {
            timeout = request.getTimeout();
        }
        if (request.hasFromCache()) {
            fromCache = request.getFromCache();
        }

        // URI params override body
        if (req.hasQueryParameter("timeout")) {
            timeout = req.getQueryParameterAsLong("timeout");
        }
        if (req.hasQueryParameter("fromCache")) {
            fromCache = req.getQueryParameterAsBoolean("fromCache");
        }

        List<NamedObjectId> ids = request.getIdList();
        List<ParameterValue> pvals = doGetParameterValues(processor, req.getUser(), ids, fromCache, timeout);

        BulkGetParameterValueResponse.Builder responseb = BulkGetParameterValueResponse.newBuilder();
        responseb.addAllValue(pvals);
        completeOK(req, responseb.build());
    }

    private List<ParameterValue> doGetParameterValues(Processor processor, User user, List<NamedObjectId> ids,
            boolean fromCache, long timeout) throws HttpException {
        if (timeout > 60000) {
            throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
        }

        ParameterRequestManager prm = processor.getParameterRequestManager();
        MyConsumer myConsumer = new MyConsumer();
        ParameterWithIdRequestHelper pwirh = new ParameterWithIdRequestHelper(prm, myConsumer);
        List<ParameterValue> pvals = new ArrayList<>();
        try {
            if (fromCache) {
                List<ParameterValueWithId> l;
                l = pwirh.getValuesFromCache(ids, user);
                for (ParameterValueWithId pvwi : l) {
                    pvals.add(pvwi.toGbpParameterValue());
                }
            } else {

                int reqId = pwirh.addRequest(ids, user);
                long t0 = System.currentTimeMillis();
                long t1;
                while (true) {
                    t1 = System.currentTimeMillis();
                    long remaining = timeout - (t1 - t0);
                    List<ParameterValueWithId> l = myConsumer.queue.poll(remaining, TimeUnit.MILLISECONDS);
                    if (l == null) {
                        break;
                    }

                    for (ParameterValueWithId pvwi : l) {
                        pvals.add(pvwi.toGbpParameterValue());
                    }
                    // TODO: this may not be correct: if we get a parameter multiple times, we stop here before
                    // receiving all parameters
                    if (pvals.size() == ids.size()) {
                        break;
                    }
                }
                pwirh.removeRequest(reqId);
            }
        } catch (InvalidIdentification e) {
            // TODO - send the invalid parameters in a parsable form
            throw new BadRequestException("Invalid parameters: " + e.getInvalidParameters().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private SoftwareParameterManagerIf verifySoftwareParameterManager(Processor processor, DataSource ds) throws BadRequestException {
        SoftwareParameterManagerIf mgr = processor.getParameterRequestManager().getSoftwareParameterManager(ds);
        if (mgr == null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this processor");
        } else {
            return mgr;
        }
    }
}
