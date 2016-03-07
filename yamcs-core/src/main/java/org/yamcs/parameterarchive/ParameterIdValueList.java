package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;

/**
 * A list of parametersIds with values all having the same timestamp
 * @author nm
 *
 */
public class ParameterIdValueList {
    final long instant;
    final int parameterGroupId;
    
    IntArray pids = new IntArray();
    
    List<ParameterValue> values = new ArrayList<>();
    
    public ParameterIdValueList(long instant, int parameterGroupId) {
        this.instant = instant;
        this.parameterGroupId = parameterGroupId;
    }
    
    public void add(int parameterId, ParameterValue v) {
        pids.add(parameterId);
        values.add(v);
    }
   

    public List<ParameterValue> getValues() {
        return values;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(TimeEncoding.toCombinedFormat(instant)+" [");
        boolean first = true;
        for(int i=0 ; i<pids.size();i++) {
            Value ev = values.get(i).getEngValue();
            if(first) first = false;
            else sb.append(", ");
            sb.append(pids.get(i)+": ("+ev.getType()+")"+ev);
        }
        sb.append("]");
        return sb.toString();
    }
}