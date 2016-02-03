package org.yamcs.web.rest;

import java.util.List;

import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;


/**
 * Expected class type for use with {@link RestReplays}
 * Adds functionality for stopping a replay, and has support for pagination
 */
public abstract class RestReplayListener implements ParameterWithIdConsumer {
    
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
    public void update(int subscriptionId, List<ParameterValueWithId> params) {
        List<ParameterValueWithId> filteredData = filter(params);
        if (filteredData == null) return;
        
        if (paginate) {
            if (rowNr >= pos) {
                if (emitted < limit) {
                    emitted++;
                    onParameterData(filteredData);
                } else {
                    requestReplayAbortion();
                }
            }
            rowNr++;
        } else {
            onParameterData(filteredData);
        }
    }
    
    /**
     * Override to filter out some replay data. Null means excluded.
     * (which also means it will not be counted towards the pagination.
     */
    public List<ParameterValueWithId> filter(List<ParameterValueWithId> params) {
        return params;
    }
    
    public abstract void onParameterData(List<ParameterValueWithId> params);
    
    public void replayFinished(){
        
    };
}
