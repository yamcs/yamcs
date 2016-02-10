package org.yamcs.parameterarchive;

import org.yamcs.protobuf.Yamcs.Value;

public interface ValueSegment {
    
    /**
     * returns Value at position index
     */
    public abstract Value getValue(int index);

    public abstract void add(int pos, Value engValue);

    public abstract BaseSegment consolidate();

    public abstract int size();

}
