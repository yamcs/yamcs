package org.yamcs.web.rest;

import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.ValueUtility;

import com.google.protobuf.MessageLite;

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
    public void onNewData(ProtoDataType type, MessageLite data) {
        onParameterData((ParameterData) data); 
    }
    
    public abstract void onParameterData(ParameterData pdata);
    
    @Override
    public MessageLite filter(ProtoDataType type, MessageLite data) {
        if (noRepeat) {
            ParameterData pdata = (ParameterData) data;
            ParameterData.Builder pdatab = ParameterData.newBuilder(pdata);
            pdatab.clearParameter();
            for (ParameterValue pval : pdata.getParameterList()) {
                if (!ValueUtility.equals(lastValue, pval.getEngValue())) {
                    pdatab.addParameter(pval);
                }
                lastValue = pval.getEngValue();
            }
            return (pdatab.getParameterCount() > 0) ? pdatab.build() : null;
        } else {
            return data;
        }
    }
}
