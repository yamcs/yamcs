package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.MessageLite;

/**
 * Performs replays for command history
 * 
 * @author nm
 *
 */
public class CommandHistoryReplayHandler implements ReplayHandler {
    private ReplayOptions repl;

    public CommandHistoryReplayHandler(String instance) {
    }

    @Override
    public void setRequest(ReplayOptions newRequest) {
        this.repl = newRequest;
    }

    @Override
    public String getSelectCmd() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.CMD_HISTORY.getNumber())
                .append(",* from " + CommandHistoryRecorder.TABLE_NAME);
        appendTimeClause(sb, repl);

        CommandHistoryReplayRequest cmdHistReq = repl.getCommandHistoryRequest();
        if (cmdHistReq.getNameFilterCount() > 0) {
            if (repl.hasStart() || (repl.hasStop())) {
                sb.append(" AND ");
            } else {
                sb.append(" WHERE ");
            }
            sb.append("cmdName IN (");
            boolean first = true;

            for (NamedObjectId id : cmdHistReq.getNameFilterList()) {
                // TODO - do something with the namespace
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                String cmdName = sanitize(id.getName());
                sb.append("'").append(cmdName).append("'");
            }
            sb.append(")");
        }

        if (repl.isReverse()) {
            sb.append(" ORDER DESC");
        }
        return sb.toString();
    }

    private String sanitize(String name) {
        return name.replace("'", "").replace("\n", "");
    }

    @Override
    public MessageLite transform(Tuple t) {
        return GPBHelper.tupleToCommandHistoryEntry(t);
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub
    }

    static void appendTimeClause(StringBuilder sb, ReplayOptions request) {
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
}
