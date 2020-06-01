package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ConnectedClient;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.AbstractProcessingApi;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.DeleteProcessorRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.GetParameterValueRequest;
import org.yamcs.protobuf.GetProcessorRequest;
import org.yamcs.protobuf.ListProcessorTypesResponse;
import org.yamcs.protobuf.ListProcessorsRequest;
import org.yamcs.protobuf.ListProcessorsResponse;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ProcessorManagementRequest;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SetParameterValueRequest;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.SubscribeParametersData;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.SubscribeProcessorsRequest;
import org.yamcs.protobuf.SubscribeTMStatisticsRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.Empty;

public class ProcessingApi extends AbstractProcessingApi<Context> {

    @Override
    public void listProcessorTypes(Context ctx, Empty request, Observer<ListProcessorTypesResponse> observer) {
        ListProcessorTypesResponse.Builder response = ListProcessorTypesResponse.newBuilder();
        List<String> processorTypes = ProcessorFactory.getProcessorTypes();
        Collections.sort(processorTypes);
        response.addAllTypes(processorTypes);
        observer.complete(response.build());
    }

    @Override
    public void listProcessors(Context ctx, ListProcessorsRequest request, Observer<ListProcessorsResponse> observer) {
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        if (request.hasInstance()) {
            YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(request.getInstance());
            for (Processor processor : ysi.getProcessors()) {
                response.addProcessors(toProcessorInfo(processor, true));
            }
        } else {
            for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
                for (Processor processor : ysi.getProcessors()) {
                    response.addProcessors(toProcessorInfo(processor, true));
                }
            }
        }

        observer.complete(response.build());
    }

    @Override
    public void getProcessor(Context ctx, GetProcessorRequest request, Observer<ProcessorInfo> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());

        ProcessorInfo pinfo = toProcessorInfo(processor, true);
        observer.complete(pinfo);
    }

    @Override
    public void deleteProcessor(Context ctx, DeleteProcessorRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlProcessor);

        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.isReplay()) {
            throw new BadRequestException("Cannot delete a non-replay processor");
        }

        processor.quit();
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void createProcessor(Context ctx, CreateProcessorRequest request, Observer<Empty> observer) {
        String yamcsInstance = ManagementApi.verifyInstance(request.getInstance());

        if (!request.hasName()) {
            throw new BadRequestException("No processor name was specified");
        }
        String processorName = request.getName();

        if (!request.hasType()) {
            throw new BadRequestException("No processor type was specified");
        }
        String processorType = request.getType();

        ProcessorManagementRequest.Builder reqb = ProcessorManagementRequest.newBuilder();
        reqb.setInstance(yamcsInstance);
        reqb.setName(processorName);
        reqb.setType(processorType);
        if (request.hasPersistent()) {
            reqb.setPersistent(request.getPersistent());
        }
        Set<Integer> clientIds = new HashSet<>(request.getClientIdList());
        // this will remove any invalid clientIds from the set
        verifyPermissions(reqb.getPersistent(), processorType, clientIds, ctx.user);

        if (request.hasConfig()) {
            reqb.setConfig(request.getConfig());
        }

        reqb.addAllClientId(clientIds);
        ManagementService mservice = ManagementService.getInstance();
        try {
            mservice.createProcessor(reqb.build(), ctx.user.getName());
            observer.complete(Empty.getDefaultInstance());
        } catch (YamcsException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void editProcessor(Context ctx, EditProcessorRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlProcessor);

        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.isReplay()) {
            throw new BadRequestException("Cannot update a non-replay processor");
        }

        if (request.hasState()) {
            switch (request.getState().toLowerCase()) {
            case "running":
                processor.resume();
                break;
            case "paused":
                processor.pause();
                break;
            default:
                throw new BadRequestException("Invalid processor state '" + request.getState() + "'");
            }
        }

        if (request.hasSeek()) {
            long seek = TimeEncoding.fromProtobufTimestamp(request.getSeek());
            processor.seek(seek);
        }

        String speed = null;
        if (request.hasSpeed()) {
            speed = request.getSpeed().toLowerCase();
        }
        if (speed != null) {
            ReplaySpeed replaySpeed;
            if ("afap".equals(speed)) {
                replaySpeed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build();
            } else if (speed.endsWith("x")) {
                try {
                    float factor = Float.parseFloat(speed.substring(0, speed.length() - 1));
                    replaySpeed = ReplaySpeed.newBuilder()
                            .setType(ReplaySpeedType.REALTIME)
                            .setParam(factor).build();
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Speed factor is not a valid number");
                }

            } else {
                try {
                    int fixedDelay = Integer.parseInt(speed);
                    replaySpeed = ReplaySpeed.newBuilder()
                            .setType(ReplaySpeedType.FIXED_DELAY)
                            .setParam(fixedDelay).build();
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Fixed delay value is not an integer");
                }
            }
            processor.changeSpeed(replaySpeed);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void getParameterValue(Context ctx, GetParameterValueRequest request,
            Observer<ParameterValue> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());

        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        NamedObjectId id = MdbApi.verifyParameterId(ctx, mdb, request.getName());

        long timeout = request.hasTimeout() ? request.getTimeout() : 10000;
        boolean fromCache = request.hasFromCache() ? request.getFromCache() : true;

        List<NamedObjectId> ids = Arrays.asList(id);
        List<ParameterValue> pvals = doGetParameterValues(processor, ctx.user, ids, fromCache, timeout);

        ParameterValue pval;
        if (pvals.isEmpty()) {
            pval = ParameterValue.newBuilder().setId(id).build();
        } else {
            pval = pvals.get(0);
        }

        observer.complete(pval);
    }

    @Override
    public void setParameterValue(Context ctx, SetParameterValueRequest request, Observer<Empty> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        ParameterWithId pid = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());
        ctx.checkObjectPrivileges(ObjectPrivilegeType.WriteParameter, pid.getParameter().getQualifiedName());

        SoftwareParameterManager mgr = verifySoftwareParameterManager(processor, pid.getParameter().getDataSource());

        Value v = ValueUtility.fromGpb(request.getValue());
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
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public Observer<SubscribeParametersRequest> subscribeParameters(Context ctx,
            Observer<SubscribeParametersData> observer) {
        SubscribeParameterObserver clientObserver = new SubscribeParameterObserver(ctx.user, observer);
        observer.setCancelHandler(() -> clientObserver.complete());
        return clientObserver;
    }

    @Override
    public void subscribeProcessors(Context ctx, SubscribeProcessorsRequest request, Observer<ProcessorInfo> observer) {
        String instance = null;
        String processor = null;
        if (request.hasInstance()) {
            instance = ManagementApi.verifyInstance(request.getInstance());
            if (request.hasProcessor()) {
                processor = verifyProcessor(request.getInstance(), request.getProcessor()).getName();
            }
        }

        String fInstance = instance;
        String fProcessor = processor;
        ManagementListener listener = new ManagementListener() {
            @Override
            public void processorAdded(ProcessorInfo info) {
                maybeEmit(info);
            }

            @Override
            public void processorStateChanged(ProcessorInfo info) {
                maybeEmit(info);
            }

            @Override
            public void processorClosed(ProcessorInfo info) {
                maybeEmit(info);
            }

            void maybeEmit(ProcessorInfo info) {
                if (fInstance == null || fInstance.equals(info.getInstance())) {
                    if (fProcessor == null || fProcessor.equals(info.getName())) {
                        observer.next(info);
                    }
                }
            }
        };

        observer.setCancelHandler(() -> ManagementService.getInstance().removeManagementListener(listener));
        ManagementService.getInstance().addManagementListener(listener);
    }

    @Override
    public void batchGetParameterValues(Context ctx, BatchGetParameterValuesRequest request,
            Observer<BatchGetParameterValuesResponse> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());

        if (request.getIdCount() == 0) {
            throw new BadRequestException("Empty parameter list");
        }

        long timeout = request.hasTimeout() ? request.getTimeout() : 10000;
        boolean fromCache = request.hasFromCache() ? request.getFromCache() : true;

        List<NamedObjectId> ids = request.getIdList();
        List<ParameterValue> pvals = doGetParameterValues(processor, ctx.user, ids, fromCache, timeout);

        BatchGetParameterValuesResponse.Builder responseb = BatchGetParameterValuesResponse.newBuilder();
        responseb.addAllValue(pvals);
        observer.complete(responseb.build());
    }

    @Override
    public void batchSetParameterValues(Context ctx, BatchSetParameterValuesRequest request,
            Observer<Empty> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());
        ParameterRequestManager prm = processor.getParameterRequestManager();

        List<NamedObjectId> idList = request.getRequestList().stream().map(r -> r.getId()).collect(Collectors.toList());
        List<ParameterWithId> pidList;
        try {
            pidList = ParameterWithIdRequestHelper.checkNames(prm, idList);
        } catch (InvalidIdentification e) {
            throw new BadRequestException("InvalidIdentification: " + e.getMessage());
        }
        ctx.checkObjectPrivileges(ObjectPrivilegeType.WriteParameter,
                pidList.stream().map(p -> p.getParameter().getQualifiedName()).collect(Collectors.toList()));

        Map<DataSource, List<org.yamcs.parameter.ParameterValue>> pvmap = new HashMap<>();
        for (int i = 0; i < pidList.size(); i++) {
            BatchSetParameterValuesRequest.SetParameterValueRequest r = request.getRequest(i);
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
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeTMStatistics(Context ctx, SubscribeTMStatisticsRequest request,
            Observer<Statistics> observer) {
        Processor processor = verifyProcessor(request.getInstance(), request.getProcessor());

        ManagementListener listener = new ManagementListener() {
            @Override
            public void statisticsUpdated(Processor statsProcessor, Statistics stats) {
                if (statsProcessor.equals(processor)) {
                    observer.next(stats);
                }
            }
        };
        observer.setCancelHandler(() -> ManagementService.getInstance().removeManagementListener(listener));
        ManagementService.getInstance().addManagementListener(listener);
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

    public static ProcessorInfo toProcessorInfo(Processor processor, boolean detail) {
        ProcessorInfo.Builder b;
        if (detail) {
            ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
            b = ProcessorInfo.newBuilder(pinfo);
        } else {
            b = ProcessorInfo.newBuilder().setName(processor.getName());
        }

        String instance = processor.getInstance();
        String name = processor.getName();

        for (ServiceWithConfig serviceWithConfig : processor.getServices()) {
            b.addServices(ManagementApi.toServiceInfo(serviceWithConfig, instance, name));
        }
        return b.build();
    }

    private void verifyPermissions(boolean persistent, String processorType, Set<Integer> clientIds, User user)
            throws ForbiddenException {
        String username = user.getName();
        if (!user.hasSystemPrivilege(SystemPrivilege.ControlProcessor)) {
            if (persistent) {
                throw new ForbiddenException("No permission to create persistent processors");
            }
            if (!"Archive".equals(processorType)) {
                throw new ForbiddenException("No permission to create processors of type " + processorType);
            }
            verifyClientsBelongToUser(username, clientIds);
        }
    }

    /**
     * verifies that clients with ids are all belonging to this username. If not, throw a ForbiddenException If there is
     * any invalid id (maybe client disconnected), remove it from the set
     */
    public static void verifyClientsBelongToUser(String username, Set<Integer> clientIds) throws ForbiddenException {
        ManagementService mgrsrv = ManagementService.getInstance();
        for (Iterator<Integer> it = clientIds.iterator(); it.hasNext();) {
            int id = it.next();
            ConnectedClient client = mgrsrv.getClient(id);
            if (client == null) {
                it.remove();
            } else {
                if (!username.equals(client.getUser().getName())) {
                    throw new ForbiddenException("Not allowed to connect clients other than your own");
                }
            }
        }
    }

    public static Processor verifyProcessor(String instance, String processorName) {
        YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(instance);
        Processor processor = ysi.getProcessor(processorName);
        if (processor == null) {
            throw new NotFoundException("No processor '" + processorName + "' within instance '" + instance + "'");
        } else {
            return processor;
        }
    }
}
