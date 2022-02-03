package org.yamcs.http.audit;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Struct;

public class AuditRecord {

    public final long time;
    public final int seqNum;
    public final String service;
    public final String method;
    public final String user;
    public final String summary;
    public final Struct request;

    public AuditRecord(Tuple tuple) {
        time = tuple.getTimestampColumn(AuditLogDb.CNAME_RECTIME);
        seqNum = tuple.getColumn(AuditLogDb.CNAME_SEQNUM);
        service = tuple.getColumn(AuditLogDb.CNAME_SERVICE);
        method = tuple.getColumn(AuditLogDb.CNAME_METHOD);
        user = tuple.getColumn(AuditLogDb.CNAME_USER);
        summary = tuple.getColumn(AuditLogDb.CNAME_SUMMARY);
        request = tuple.getColumn(AuditLogDb.CNAME_REQUEST);
    }

    public org.yamcs.protobuf.audit.AuditRecord toProtobuf() {
        org.yamcs.protobuf.audit.AuditRecord.Builder b = org.yamcs.protobuf.audit.AuditRecord.newBuilder()
                .setTime(TimeEncoding.toProtobufTimestamp(time))
                .setService(service)
                .setMethod(method)
                .setUser(user);
        if (summary != null) {
            b.setSummary(summary);
        }
        if (request != null) {
            b.setRequest(request);
        }
        return b.build();
    }
}
