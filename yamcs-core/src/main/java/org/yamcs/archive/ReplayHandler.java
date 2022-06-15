package org.yamcs.archive;

import org.yamcs.YamcsException;
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

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }

        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        sqlb.descend(request.isReverse());
        return sqlb;
    }

}
