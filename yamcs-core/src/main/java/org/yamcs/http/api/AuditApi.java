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
import org.yamcs.security.SystemPrivilege;
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
        String instance = ManagementApi.verifyInstance(request.getInstance(), true);
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            if (!ctx.user.isSuperuser()) {
                throw new ForbiddenException("Insufficient privileges");
            }
        }

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

        // Currently, because there is not yet a way for managing permissions related to
        // instance-level logging, we have to be a bit strict and manual about it, and
        // thereby limit only to a few use cases.
        if (filter.getServices().isEmpty()) {
            throw new ForbiddenException("Can only query specific instance activity");
        }
        for (String service : filter.getServices()) {
            if (service.equals(LinksApi.class.getSimpleName())) {
                ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);
            } else if (service.equals(QueueApi.class.getSimpleName())) {
                ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandQueue);
            } else {
                throw new ForbiddenException("The specified service cannot be queried");
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
