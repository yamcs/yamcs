package org.yamcs.web.rest;

import org.yamcs.archive.ReplayListener;
import org.yamcs.protobuf.Yamcs.ReplayStatus;

/**
 * Expected class type for use with {@link RestReplays}
 * Adds functionality for stopping a replay.
 */
public abstract class RestReplayListener implements ReplayListener {
    
    private boolean abortReplay = false;
    
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
}
