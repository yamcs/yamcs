package org.yamcs.parameterarchive;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * an array of values for one {@link ParameterId}
 * 
 * @author nm
 *
 */
public class ParameterValueArray {
    final long[] timestamps;
    // values is an array of primitives
    final ValueArray engValues;
    final ValueArray rawValues;
    final ParameterStatus[] paramStatus;
    
    public ParameterValueArray(long timestamps[], ValueArray engValues, ValueArray rawValues, ParameterStatus[] paramStatus) {
        this.timestamps = timestamps;
        this.engValues = engValues;
        this.rawValues = rawValues;
        this.paramStatus = paramStatus;
    }

    public long[] getTimestamps() {
        return timestamps;
    }

    public ValueArray getEngValues() {
        return engValues;
    }

    public ValueArray getRawValues() {
        return rawValues;
    }

    public Type getEngType() {
        return engValues.getType();
    }

    public ParameterStatus[] getStatuses() {
        return paramStatus;
    }
    
    /**
     * Return engineering value of the parameter on position idx
     * @param idx
     * @return
     */
    Value getEngValue(int idx) {
        return engValues.getValue(idx);
    }

    public int size() {
        return timestamps.length;
    }
}
