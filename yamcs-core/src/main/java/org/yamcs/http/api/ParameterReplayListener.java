package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;

/**
 * Expected class type for use with {@link org.yamcs.http.api.ReplayFactory} Adds functionality for stopping a replay,
 * and has support for pagination
 */
public abstract class ParameterReplayListener extends Service.Listener implements ParameterWithIdConsumer {

    private final boolean paginate;
    private final long pos;
    private final int limit;

    private int rowNr = 0; // zero-based
    private int emitted = 0;

    private boolean abortReplay = false;

    private boolean noRepeat = false;
    private Value lastValue;

    public ParameterReplayListener() {
        this(-1, -1);
    }

    public ParameterReplayListener(long pos, int limit) {
        if (pos == -1 && limit == -1) {
            paginate = false;
            this.pos = pos;
            this.limit = limit;
        } else {
            paginate = true;
            this.pos = Math.max(pos, 0);
            this.limit = Math.max(limit, 0);
        }
    }

    public void setNoRepeat(boolean noRepeat) {
        this.noRepeat = noRepeat;
    }

    public void requestReplayAbortion() {
        abortReplay = true;
    }

    public boolean isReplayAbortRequested() {
        return abortReplay;
    }

    @Override
    public void failed(State from, Throwable failure) {
        replayFailed(failure);
    }

    @Override
    public void terminated(State from) {
        replayFinished();
    }

    @Override
    public void update(int subscriptionId, List<ParameterValueWithId> params) {
        params = prefilter(params);
        if (params == null) {
            return;
        }

        params = filter(params);
        if (params == null) {
            return;
        }

        if (paginate) {
            if (rowNr >= pos) {
                if (emitted < limit) {
                    emitted++;
                    onParameterData(params);
                } else {
                    requestReplayAbortion();
                }
            }
            rowNr++;
        } else {
            onParameterData(params);
        }
    }

    // fast path for one parameter only
    public void update(ParameterValueWithId pvwid) {
        pvwid = prefilter(pvwid);
        if (pvwid == null) {
            return;
        }

        pvwid = filter(pvwid);
        if (pvwid == null) {
            return;
        }

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

    // Default filtering. Not overridable by implementations
    private List<ParameterValueWithId> prefilter(List<ParameterValueWithId> params) {
        if (noRepeat) {
            List<ParameterValueWithId> plist = new ArrayList<>();

            for (ParameterValueWithId pvalid : params) {
                ParameterValue pval = pvalid.getParameterValue();
                if (!ValueUtility.equals(lastValue, pval.getEngValue())) {
                    plist.add(pvalid);
                }
                lastValue = pval.getEngValue();
            }
            return (plist.size() > 0) ? plist : null;
        } else {
            return params;
        }
    }

    // Default filtering. Not overridable by implementations
    private ParameterValueWithId prefilter(ParameterValueWithId pvwid) {
        if (noRepeat) {
            ParameterValue pval = pvwid.getParameterValue();
            if (!ValueUtility.equals(lastValue, pval.getEngValue())) {
                lastValue = pval.getEngValue();
                return pvwid;
            } else {
                return null;
            }
        } else {
            return pvwid;
        }
    }

    /**
     * Override to filter out some replay data. Null means excluded. (which also means it will not be counted towards
     * the pagination).
     * 
     * @return filtered data
     */
    public List<ParameterValueWithId> filter(List<ParameterValueWithId> params) {
        return params;
    }

    // fast path of the above with one parameter
    public ParameterValueWithId filter(ParameterValueWithId pvwid) {
        return pvwid;
    }

    protected void onParameterData(List<ParameterValueWithId> params) {
    }

    protected void onParameterData(ParameterValueWithId pvwid) {
    }

    public abstract void replayFinished();

    public abstract void replayFailed(Throwable t);
}
