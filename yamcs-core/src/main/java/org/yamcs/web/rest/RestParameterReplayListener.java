package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.ValueUtility;


/**
 * Filters the replay of parameters. Extracted in an abstract class because it's
 * used in multiple places
 */
public abstract class RestParameterReplayListener extends RestReplayListener {
    
    private boolean noRepeat;
    
    private Value lastValue;
    
    public RestParameterReplayListener() {
        super();
    }
    
    public RestParameterReplayListener(long pos, int limit) {
        super(pos, limit);
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
}
