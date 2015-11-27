package org.yamcs.web.rest;

import org.yamcs.archive.ReplayListener;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;

import com.google.protobuf.MessageLite;

/**
 * Expected class type for use with {@link RestReplays}
 * Adds functionality for stopping a replay, and has support for pagination
 */
public abstract class RestReplayListener implements ReplayListener {
    
    private final boolean paginate;
    private final long pos;
    private final int limit;
    
    private int rowNr = 0; // zero-based
    private int emitted = 0;
    
    private boolean abortReplay = false;
    
    public RestReplayListener() {
        paginate = false;
        pos = -1;
        limit = -1;
    }
    
    public RestReplayListener(long pos, int limit) {
        paginate = true;
        this.pos = Math.max(pos, 0);
        this.limit = Math.max(limit, 0);
    }
    
    public void requestReplayAbortion() {
        abortReplay = true;
    }
    
    public boolean isReplayAbortRequested() {
        return abortReplay;
    }

    @Override
    public void stateChanged(ReplayStatus rs) {
        // NOP
    }
    
    @Override
    public void newData(ProtoDataType type, MessageLite data) {
        MessageLite filteredData = filter(type, data);
        if (filteredData == null) return;
        
        if (paginate) {
            if (rowNr >= pos) {
                if (emitted < limit) {
                    emitted++;
                    onNewData(type, filteredData);
                } else {
                    requestReplayAbortion();
                }
            }
            rowNr++;
        } else {
            onNewData(type, filteredData);
        }
    }
    
    /**
     * Override to filter out some replay data. Null means excluded.
     * (which also means it will not be counted towards the pagination.
     */
    public MessageLite filter(ProtoDataType type, MessageLite data) {
        return data;
    }
    
    public abstract void onNewData(ProtoDataType type, MessageLite data);
}
