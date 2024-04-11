package org.yamcs.archive;

import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.mdb.Mdb;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

import com.google.protobuf.MessageLite;

/**
 * Performs replays for command history
 * 
 * @author nm
 *
 */
public class CommandHistoryReplayHandler implements ReplayHandler {
    private ReplayOptions repl;
    private Mdb mdb;

    public CommandHistoryReplayHandler(String instance, Mdb mdb) {
        this.mdb = mdb;
    }

    @Override
    public void setRequest(ReplayOptions newRequest) {
        this.repl = newRequest;
    }

    @Override
    public SqlBuilder getSelectCmd() {
        SqlBuilder sqlb = ReplayHandler.init(CommandHistoryRecorder.TABLE_NAME, ProtoDataType.CMD_HISTORY, repl);

        CommandHistoryReplayRequest cmdHistReq = repl.getCommandHistoryRequest();
        if (cmdHistReq.getNameFilterCount() > 0) {
            // TODO - do something with the namespace
            List<String> cmdNames = cmdHistReq.getNameFilterList().stream().map(id -> id.getName())
                    .collect(Collectors.toList());
            sqlb.whereColIn("cmdName", cmdNames);
        }

        return sqlb;
    }

    @Override
    public MessageLite transform(Tuple t) {
        return GPBHelper.tupleToCommandHistoryEntry(t, mdb);
    }
}
