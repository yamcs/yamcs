package org.yamcs.parameterarchive;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * an array of values for one {@link ParameterId}
 *
 */
public class ParameterValueArray {
    final long[] timestamps;
    // engValues and rawValues are arrays of primitives
    final ValueArray engValues;
    final ValueArray rawValues;
    final ParameterStatus[] paramStatus;

    public ParameterValueArray(long timestamps[], ValueArray engValues, ValueArray rawValues,
            ParameterStatus[] paramStatus) {
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

    /**
     * @return the type of the engineering values or {@link Type#NONE} if the engineering values were not requested
     */
    public Type getEngType() {
        return engValues == null ? Type.NONE : engValues.getType();
    }

    /**
     * @return the type of the raw values or {@link Type#NONE} if there are no raw values in this segment (either
     *         because the parameter does not have a raw value or because the raw values were not requested/extracted)
     */
    public Type getRawType() {
        return rawValues == null ? Type.NONE : rawValues.getType();
    }

    public ParameterStatus[] getStatuses() {
        return paramStatus;
    }

    /**
     * Return engineering value of the parameter on position idx
     *
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
