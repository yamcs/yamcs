package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.web.InternalServerErrorException;


/**
 * Filters the replay of parameters. Extracted in an abstract class because it's
 * used in multiple places
 */
public abstract class RestParameterReplayListener extends RestReplayListener {
    private boolean noRepeat;
    private Value lastValue;
    final protected RestRequest req;
    /**
     * 
     * @param cf is the completable future of the rest request - used to end exceptionally in case of error
     */
    public RestParameterReplayListener(RestRequest req) {
        super();
        this.req = req;
    }
    /**
     * 
     * @param pos
     * @param limit
     * @param cf is the completable future of the rest request - used to end exceptionally in case of error
     */
    public RestParameterReplayListener(long pos, int limit, RestRequest req) {
        super(pos, limit);
        this.req = req;
    }
    
    public void setNoRepeat(boolean noRepeat) {
        this.noRepeat = noRepeat;
    }
    
    @Override
    public List<ParameterValueWithId> filter(List<ParameterValueWithId> params) {
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
    
    @Override
    public ParameterValueWithId filter(ParameterValueWithId pvwid) {
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

    public void replayFailed(Throwable t){
        RestHandler.completeWithError(req, new InternalServerErrorException(t));
    }
}
