package org.yamcs.parameterarchive;

import org.yamcs.protobuf.Pvalue.ParameterStatus;

/**
 * an array of values for one parameter
 * @author nm
 *
 */
public class ParameterValueArray {
    final int parameterId;
    final long[] timestamps;
    //values is an array of primitives
    final Object engValues;
    final Object rawValues;
    final ParameterStatus[] paramStatus;
    
    public ParameterValueArray(int parameterId, long timestamps[], Object values, Object rawValues, ParameterStatus[] paramStatus) {
        this.parameterId = parameterId;
        this.timestamps = timestamps;
        this.engValues = values;
        this.rawValues = rawValues;
        this.paramStatus = paramStatus;
    }
    
    public long[] getTimestamps() {
        return timestamps;
    }
    
    public Object getEngValues() {
        return engValues;
    }
    
    public Object getRawValues() {
        return rawValues;
    }
}
