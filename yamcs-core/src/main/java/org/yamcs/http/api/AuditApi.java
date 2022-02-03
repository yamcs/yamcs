package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.NotFoundException;
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

    @Override
    public void listAuditRecords(Context ctx, ListAuditRecordsRequest request,
            Observer<ListAuditRecordsResponse> observer) {
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }

        AuditLog auditLog = verifyAuditLog();

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

        List<AuditRecord> records = new ArrayList<>();
        auditLog.listRecords(limit, next, filter, new AuditRecordListener() {
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

    private AuditLog verifyAuditLog() {
        List<AuditLog> auditLogs = YamcsServer.getServer().getGlobalServices(AuditLog.class);
        if (auditLogs.isEmpty()) {
            throw new NotFoundException("No audit service found");
        }
        return auditLogs.get(0);
    }
}
