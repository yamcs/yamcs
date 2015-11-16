package org.yamcs.web.rest;

import org.yamcs.protobuf.Yamcs.ProtoDataType;

import com.google.protobuf.MessageLite;

/**
 * Reusable mechanism to process a paged set of stream data data. Should ultimately be replaced
 * by implementing the limit clause in StreamSQL.
 */
public abstract class LimitedReplayListener extends RestReplayListener {
    
    private final long pos;
    private final int limit;
    
    private int rowNr = 0; // zero-based
    private int emitted = 0;
    
    public LimitedReplayListener(long pos, int limit) {
        this.pos = Math.max(pos, 0);
        this.limit = Math.max(limit, 0);
    }
    
    @Override
    public void newData(ProtoDataType type, MessageLite data) {
        if (rowNr >= pos) {
            if (emitted < limit) {
                emitted++;
                onNewData(type, data);
            } else {
                requestReplayAbortion();
            }
        }
        rowNr++;
    }

    public abstract void onNewData(ProtoDataType type, MessageLite data);
}
