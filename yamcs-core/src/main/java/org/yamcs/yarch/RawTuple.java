package org.yamcs.yarch;

import java.util.Comparator;

import com.google.common.primitives.UnsignedBytes;


public abstract class RawTuple implements Comparable<RawTuple>{
    int index; //used for sorting tuples with equals keys
    protected abstract byte[] getKey();
    protected abstract byte[] getValue();
    
    static Comparator<byte[]> bytesComparator=UnsignedBytes.lexicographicalComparator();

    public static Comparator<RawTuple> reverseComparator = new Comparator<RawTuple>() {
        @Override
        public int compare(RawTuple o1, RawTuple o2) {
            return -o1.compareTo(o2);
        }
    };

    public RawTuple(int index) {
        this.index=index;
    }

    @Override
    public int compareTo(RawTuple o) {
        int c = bytesComparator.compare(getKey(), o.getKey());
        if(c!=0) return c;
        return (index-o.index);
    }
}