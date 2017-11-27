package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.cmdhistory.Util;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.yarch.Tuple;

public class CmdHistoryTupleTranslator implements TupleTranslator {

    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {
        CommandHistoryEntry che = Util.transform(tuple);
        Protocol.encode(msg, che);
        return msg;
    }

    @Override
    public Tuple buildTuple(ClientMessage msg) {
        try {
            CommandHistoryEntry che=(CommandHistoryEntry)Protocol.decode(msg, CommandHistoryEntry.newBuilder());
            return Util.transform(che);
        } catch (YamcsApiException e) {
            throw new IllegalArgumentException(e.toString(), e);
        }
    }
}
