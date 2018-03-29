package org.yamcs.parameterarchive;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;

public interface ValueSegment {
    
    /**
     * returns Value at position index
     * @param index 
     * @return the value at the index
     */
    public abstract Value getValue(int index);

    public abstract void add(int pos, Value engValue);

    public abstract BaseSegment consolidate();

    public abstract int size();

    /**
     * returns an array containing the values in the range [posStart, posStop) if ascending or [posStop, posStart) if descending
     *
     * @param posStart
     * @param posStop
     * @param ascending
     * @return an array containing the values in the specified range
     */
    public abstract ValueArray getRange(int posStart, int posStop, boolean ascending) ;
}
