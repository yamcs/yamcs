package org.yamcs.archive;

import org.yamcs.YamcsException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

public interface ReplayHandler {

    void setRequest(ReplayOptions req) throws YamcsException;

    SqlBuilder getSelectCmd();

    Object transform(Tuple t);

    static SqlBuilder init(String tableName, ProtoDataType type, ReplayOptions request) {
        SqlBuilder sqlb = new SqlBuilder(tableName);
        sqlb.select(Integer.toString(type.getNumber()), "*");

        long afterOrEqual = request.getRangeStart();
        if (!request.isReverse()) {
            afterOrEqual = maxTime(afterOrEqual, request.getPlayFrom());
        }
        if (afterOrEqual != TimeEncoding.INVALID_INSTANT) {
            sqlb.whereColAfterOrEqual("gentime", afterOrEqual);
        }

        long before = request.getRangeStop();
        if (request.isReverse()) {
            before = minTime(before, request.getPlayFrom());
        }

        if (before != TimeEncoding.INVALID_INSTANT) {
            sqlb.whereColBefore("gentime", before);
        }

        sqlb.descend(request.isReverse());
        return sqlb;
    }

    private static long maxTime(long a, long b) {
        if (a == TimeEncoding.INVALID_INSTANT) {
            return b;
        } else if (b == TimeEncoding.INVALID_INSTANT) {
            return a;
        } else {
            return Math.max(a, b);
        }
    }

    private static long minTime(long a, long b) {
        if (a == TimeEncoding.INVALID_INSTANT) {
            return b;
        } else if (b == TimeEncoding.INVALID_INSTANT) {
            return a;
        } else {
            return Math.min(a, b);
        }
    }
}
