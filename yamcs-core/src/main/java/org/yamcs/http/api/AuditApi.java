package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.http.audit.AuditRecordFilter;
import org.yamcs.http.audit.AuditRecordListener;
import org.yamcs.protobuf.audit.AbstractAuditApi;
import org.yamcs.protobuf.audit.AuditRecord;
import org.yamcs.protobuf.audit.ListAuditRecordsRequest;
import org.yamcs.protobuf.audit.ListAuditRecordsResponse;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

public class AuditApi extends AbstractAuditApi<Context> {

    private AuditLog auditLog;

    public AuditApi(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void listAuditRecords(Context ctx, ListAuditRecordsRequest request,
            Observer<ListAuditRecordsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance(), true);

        String next = request.hasNext() ? request.getNext() : null;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        TimeInterval interval = new TimeInterval();
        if (request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        AuditRecordFilter filter = new AuditRecordFilter(interval);
        if (request.hasQ()) {
            filter.setSearch(request.getQ());
        }
        if (request.hasService()) {
            filter.addService(request.getService());
        }

        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            listGlobalActivity(ctx, limit, next, filter, observer);
        } else {
            listInstanceActivity(ctx, instance, limit, next, filter, observer);
        }
    }

    private void listGlobalActivity(Context ctx, int limit, String next, AuditRecordFilter filter,
            Observer<ListAuditRecordsResponse> observer) {

        if (filter.getServices().isEmpty()) {
            if (!ctx.user.isSuperuser()) {
                // If unspecified, try to return results for any otherwise authorised content
                var allowedServices = auditLog.getServices(ctx.user);
                if (allowedServices.isEmpty()) {
                    // Quick response, otherwise auditLog would send unfiltered data.
                    observer.complete(ListAuditRecordsResponse.getDefaultInstance());
                    return;
                } else {
                    filter.setServices(allowedServices);
                }
            }
        } else {
            for (String service : filter.getServices()) {
                if (!ctx.user.isSuperuser() && !auditLog.validateAccess(service, ctx.user)) {
                    throw new ForbiddenException("Insufficient privileges");
                }
            }
        }

        List<AuditRecord> records = new ArrayList<>();
        auditLog.listRecords(YamcsServer.GLOBAL_INSTANCE, limit, next, filter, new AuditRecordListener() {
            @Override
            public void next(org.yamcs.http.audit.AuditRecord record) {
                records.add(record.toProtobuf());
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                ListAuditRecordsResponse response = ListAuditRecordsResponse.newBuilder()
                        .addAllRecords(records)
                        .build();
                observer.complete(response);
            }
        });
    }

    private void listInstanceActivity(Context ctx, String instance, int limit, String next, AuditRecordFilter filter,
            Observer<ListAuditRecordsResponse> observer) {

        if (filter.getServices().isEmpty()) {
            throw new ForbiddenException("Can only query specific instance activity");
        }

        for (String service : filter.getServices()) {
            if (!auditLog.validateAccess(service, ctx.user)) {
                throw new ForbiddenException("Insufficient privileges");
            }
        }

        List<AuditRecord> records = new ArrayList<>();
        auditLog.listRecords(instance, limit, next, filter, new AuditRecordListener() {
            @Override
            public void next(org.yamcs.http.audit.AuditRecord record) {
                records.add(record.toProtobuf());
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                ListAuditRecordsResponse response = ListAuditRecordsResponse.newBuilder()
                        .addAllRecords(records)
                        .build();
                observer.complete(response);
            }
        });
    }
}
