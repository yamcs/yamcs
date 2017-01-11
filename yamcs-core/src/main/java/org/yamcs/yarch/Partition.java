package org.yamcs.yarch;

/**
 *  stores information about Partition of a table
 *  It is subclassed by storage engines to store additional information 
 */
public class Partition {
    
    final long start,  end; //for time based partitioning 
    final Object value; //for value based partitioning, otherwise null

    public Partition(long start, long end) {
        this.start = start;
        this.end = end;
        this.value = null;
    }

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
        return "Partition [start=" + start + ", end=" + end + ", value=" + value + "]";
    }
}
