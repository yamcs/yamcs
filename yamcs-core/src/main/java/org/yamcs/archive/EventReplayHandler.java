package org.yamcs.archive;

import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

import com.google.protobuf.MessageLite;

public class EventReplayHandler implements ReplayHandler {
    ReplayOptions request;

    @Override
    public void setRequest(ReplayOptions newRequest) {
        this.request = newRequest;
    }

    @Override
    public SqlBuilder getSelectCmd() {
        SqlBuilder sqlb = ReplayHandler.init(EventRecorder.TABLE_NAME, ProtoDataType.EVENT, request);

        return sqlb;
    }

    @Override
    public MessageLite transform(Tuple t) {
        return (Event) t.getColumn("body");
    }
}
