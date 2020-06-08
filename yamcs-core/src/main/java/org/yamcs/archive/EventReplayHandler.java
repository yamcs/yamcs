package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.Event;

import com.google.protobuf.MessageLite;

public class EventReplayHandler implements ReplayHandler {
    ReplayOptions request;

    @Override
    public void setRequest(ReplayOptions newRequest) {
        this.request = newRequest;
    }

    @Override
    public String getSelectCmd() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.EVENT.getNumber()).append(",* from events");
        appendWhereClause(sb, request);
        if (request.isReverse()) {
            sb.append(" ORDER DESC");
        }
        return sb.toString();
    }

    static void appendWhereClause(StringBuilder sb, ReplayOptions request) {
        if (request.hasStart() || (request.hasStop())) {
            sb.append(" where ");
            if (request.hasStart()) {
                sb.append(" gentime>=" + request.getStart());
                if (request.hasStop()) {
                    sb.append(" and gentime<" + request.getStop());
                }
            } else {
                sb.append(" gentime<" + request.getStop());
            }
        }
    }

    @Override
    public MessageLite transform(Tuple t) {
        return (Event) t.getColumn("body");
    }

    @Override
    public void reset() {
    }
}
