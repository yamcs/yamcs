package org.yamcs.http.api.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BatchGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BatchGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BatchSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BatchSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class ProcessorParameterRestHandler extends RestHandler {

    @Route(path = "/api/processors/{instance}/{processor}/parameters/{name*}", method = { "PUT", "POST" })
    public void setSingleParameterValue(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        ParameterWithId pid = verifyParameterWithId(req, mdb, req.getRouteParam("name"));

        SoftwareParameterManager mgr = verifySoftwareParameterManager(processor, pid.getParameter().getDataSource());

        Value v = ValueUtility.fromGpb(req.bodyAsMessage(org.yamcs.protobuf.Yamcs.Value.newBuilder()).build());
        Parameter p = pid.getParameter();
        org.yamcs.parameter.ParameterValue pv;
        if (pid.getPath() == null) {
            pv = new org.yamcs.parameter.ParameterValue(p);
        } else {
            pv = new PartialParameterValue(p, pid.getPath());
        }
        pv.setEngineeringValue(v);
        try {
            mgr.updateParameters(Arrays.asList(pv));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        completeOK(req);
    }

    @Route(path = "/api/processors/{instance}/{processor}/parameters:batchSet", method = "POST")
    public void setParameterValues(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        ParameterRequestManager prm = processor.getParameterRequestManager();

        BatchSetParameterValueRequest request = req.bodyAsMessage(BatchSetParameterValueRequest.newBuilder()).build();
        List<NamedObjectId> idList = request.getRequestList().stream().map(r -> r.getId()).collect(Collectors.toList());
        List<ParameterWithId> pidList;
        try {
            pidList = ParameterWithIdRequestHelper.checkNames(prm, idList);
        } catch (InvalidIdentification e) {
            throw new BadRequestException("InvalidIdentification: " + e.getMessage());
        }
        checkObjectPrivileges(req, ObjectPrivilegeType.WriteParameter,
                pidList.stream().map(p -> p.getParameter().getQualifiedName()).collect(Collectors.toList()));

        Map<DataSource, List<org.yamcs.parameter.ParameterValue>> pvmap = new HashMap<>();
        for (int i = 0; i < pidList.size(); i++) {
            SetParameterValueRequest r = request.getRequest(i);
            ParameterWithId pid = pidList.get(i);
            Parameter p = pid.getParameter();
            org.yamcs.parameter.ParameterValue pv;
            if (pid.getPath() == null) {
                pv = new org.yamcs.parameter.ParameterValue(p);
            } else {
                pv = new PartialParameterValue(p, pid.getPath());
            }
            pv.setEngineeringValue(ValueUtility.fromGpb(r.getValue()));
            List<org.yamcs.parameter.ParameterValue> l = pvmap.computeIfAbsent(p.getDataSource(),
                    k -> new ArrayList<>());
            l.add(pv);
        }

        for (Map.Entry<DataSource, List<org.yamcs.parameter.ParameterValue>> me : pvmap.entrySet()) {
            List<org.yamcs.parameter.ParameterValue> l = me.getValue();
            DataSource ds = me.getKey();
            SoftwareParameterManager mgr = verifySoftwareParameterManager(processor, ds);
            try {
                mgr.updateParameters(l);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
        completeOK(req);
    }

    @Route(path = "/api/processors/{instance}/{processor}/parameters/{name*}", method = "GET")
    public void getParameterValue(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        NamedObjectId id = verifyParameterId(req, mdb, req.getRouteParam("name"));

        long timeout = 10000;
        boolean fromCache = true;
        if (req.hasQueryParameter("timeout")) {
            timeout = req.getQueryParameterAsLong("timeout");
        }
        if (req.hasQueryParameter("fromCache")) {
            fromCache = req.getQueryParameterAsBoolean("fromCache");
        }

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

    @Route(path = "/api/processors/{instance}/{processor}/parameters:batchGet", method = "POST")
    public void getParameterValues(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        BatchGetParameterValueRequest request = req.bodyAsMessage(BatchGetParameterValueRequest.newBuilder()).build();
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

        BatchGetParameterValueResponse.Builder responseb = BatchGetParameterValueResponse.newBuilder();
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

    private SoftwareParameterManager verifySoftwareParameterManager(Processor processor, DataSource ds)
            throws BadRequestException {
        SoftwareParameterManager mgr = processor.getParameterRequestManager().getSoftwareParameterManager(ds);
        if (mgr == null) {
            throw new BadRequestException("SoftwareParameterManager not activated for this processor");
        } else {
            return mgr;
        }
    }
}
