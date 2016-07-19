package org.yamcs.web.rest;

import java.util.List;

import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;


/**
 * Expected class type for use with {@link org.yamcs.web.rest.archive.RestReplays}
 * Adds functionality for stopping a replay, and has support for pagination
 */
public abstract class RestReplayListener extends Service.Listener implements ParameterWithIdConsumer {
    
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
    public void failed(State from, Throwable failure) {
        replayFinished();
    }
    
    @Override
    public void terminated(State from) {
        replayFinished();
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
    
    //fast path for one parameter only
    public void update(ParameterValueWithId pvwid) {
        pvwid = filter(pvwid);
        if (pvwid == null) return;
        if (paginate) {
            if (rowNr >= pos) {
                if (emitted < limit) {
                    emitted++;
                    onParameterData(pvwid);
                } else {
                    requestReplayAbortion();
                }
            }
            rowNr++;
        } else {
            onParameterData(pvwid);
        }
    }
    /**
     * Override to filter out some replay data. Null means excluded.
     * (which also means it will not be counted towards the pagination).
     * @return filtered data
     */
    public List<ParameterValueWithId> filter(List<ParameterValueWithId> params) {
        return params;
    }
    
    //fast path of the above with one parameter
    public ParameterValueWithId filter(ParameterValueWithId pvwid) {
        return pvwid;
    }
    
    protected void onParameterData(List<ParameterValueWithId> params){};
    
    protected void onParameterData(ParameterValueWithId pvwid){};
    
    public void replayFinished(){};
}
