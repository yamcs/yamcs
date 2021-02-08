package org.yamcs.yarch;

import org.yamcs.utils.TimeEncoding;

/**
 *  stores information about Partition of a table
 *  It is subclassed by storage engines to store additional information 
 */
public class Partition {
    final protected long start, end; // for time based partitioning
    final protected Object value; // for value based partitioning, otherwise null

    public Partition(long start, long end, Object value) {
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Partition [start=" + TimeEncoding.toString(start) + ", end=" + TimeEncoding.toString(end) + ", value=" + value + "]";
    }
}
