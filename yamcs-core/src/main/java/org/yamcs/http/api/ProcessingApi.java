package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ErrorInCommand;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.api.Observer;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.management.ManagementGpbHelper;
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
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.GetParameterValueRequest;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SetParameterValueRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yaml.snakeyaml.util.UriEncoder;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class ProcessingApi extends AbstractProcessingApi<Context> {

    @Override
    public void getParameterValue(Context ctx, GetParameterValueRequest request,
            Observer<ParameterValue> observer) {
        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());

        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        NamedObjectId id = RestHandler.verifyParameterId(ctx.user, mdb, request.getName());

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
    public void setParameterValue(Context ctx, SetParameterValueRequest request,
            Observer<Empty> observer) {
        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());

        ParameterWithId pid = RestHandler.verifyParameterWithId(ctx.user, mdb, request.getName());

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
    public void batchGetParameterValues(Context ctx, BatchGetParameterValuesRequest request,
            Observer<BatchGetParameterValuesResponse> observer) {
        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());

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
        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());
        ParameterRequestManager prm = processor.getParameterRequestManager();

        List<NamedObjectId> idList = request.getRequestList().stream().map(r -> r.getId()).collect(Collectors.toList());
        List<ParameterWithId> pidList;
        try {
            pidList = ParameterWithIdRequestHelper.checkNames(prm, idList);
        } catch (InvalidIdentification e) {
            throw new BadRequestException("InvalidIdentification: " + e.getMessage());
        }
        RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.WriteParameter,
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
    public void issueCommand(Context ctx, IssueCommandRequest request, Observer<IssueCommandResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.Command);

        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String requestCommandName = UriEncoder.decode(request.getName());
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        MetaCommand cmd = RestHandler.verifyCommand(mdb, requestCommandName);

        RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.Command, cmd.getQualifiedName());

        String origin = ctx.getClientAddress();
        int sequenceNumber = 0;
        boolean dryRun = false;
        String comment = null;
        List<ArgumentAssignment> assignments = new ArrayList<>();

        if (request.hasOrigin()) { // TODO remove this override?
            origin = request.getOrigin();
        }
        if (request.hasDryRun()) {
            dryRun = request.getDryRun();
        }
        if (request.hasSequenceNumber()) {
            sequenceNumber = request.getSequenceNumber();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }
        for (Assignment a : request.getAssignmentList()) {
            assignments.add(new ArgumentAssignment(a.getName(), a.getValue()));
        }

        // Prepare the command
        PreparedCommand preparedCommand;
        try {
            preparedCommand = processor.getCommandingManager().buildCommand(cmd, assignments, origin, sequenceNumber,
                    ctx.user);
            if (comment != null && !comment.trim().isEmpty()) {
                preparedCommand.setComment(comment);
            }
            request.getAttributeList().forEach(cha -> preparedCommand.addAttribute(cha));

            // make the source - should perhaps come from the client
            StringBuilder sb = new StringBuilder();
            sb.append(cmd.getQualifiedName());
            sb.append("(");
            boolean first = true;
            for (ArgumentAssignment aa : assignments) {
                Argument a = preparedCommand.getMetaCommand().getArgument(aa.getArgumentName());
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(aa.getArgumentName()).append(": ");

                boolean needDelimiter = a != null && (a.getArgumentType() instanceof StringArgumentType
                        || a.getArgumentType() instanceof EnumeratedArgumentType);
                if (needDelimiter) {
                    sb.append("\"");
                }
                sb.append(aa.getArgumentValue());
                if (needDelimiter) {
                    sb.append("\"");
                }
            }
            sb.append(")");
            preparedCommand.setSource(sb.toString());

        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }

        // Good, now send
        CommandQueue queue;
        if (dryRun) {
            CommandQueueManager mgr = processor.getCommandingManager().getCommandQueueManager();
            queue = mgr.getQueue(ctx.user, preparedCommand);
        } else {
            queue = processor.getCommandingManager().sendCommand(ctx.user, preparedCommand);
        }

        CommandQueueEntry cqe = ManagementGpbHelper.toCommandQueueEntry(queue, preparedCommand);

        IssueCommandResponse.Builder response = IssueCommandResponse.newBuilder();
        response.setCommandQueueEntry(cqe);
        response.setSource(preparedCommand.getSource());
        response.setBinary(ByteString.copyFrom(preparedCommand.getBinary()));
        response.setHex(StringConverter.arrayToHexString(preparedCommand.getBinary()));
        observer.complete(response.build());
    }

    @Override
    public void updateCommandHistory(Context ctx, UpdateCommandHistoryRequest request, Observer<Empty> observer) {
        Processor processor = RestHandler.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        try {
            CommandId cmdId = request.getCmdId();

            for (UpdateCommandHistoryRequest.KeyValue historyEntry : request.getHistoryEntryList()) {
                processor.getCommandingManager().addToCommandHistory(cmdId, historyEntry.getKey(),
                        historyEntry.getValue(), ctx.user);
            }
        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        }

        observer.complete(Empty.getDefaultInstance());
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
