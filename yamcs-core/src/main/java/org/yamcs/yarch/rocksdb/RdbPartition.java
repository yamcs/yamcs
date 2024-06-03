package org.yamcs.yarch.rocksdb;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Partition;

public class RdbPartition extends Partition {
    /**
     * partition directory relative to the {@link Tablespace#getDataDir()}
     */
    final String dir;
    final int tbsIndex;
    
    //column name -> tbsIndex for the record containing the histogram data
    final Map<String, Integer> histoTbsIndices = new HashMap<>();
    
    public RdbPartition(int tbsIndex, long start, long end, Object v, String dir) {
        super(start, end, v);
        this.dir = dir;
        this.tbsIndex = tbsIndex;
    }
    
    @Override
    public String toString() {
        return "Partition [start=" + TimeEncoding.toString(start) + ", end=" + TimeEncoding.toString(end) +
                ", value=" + value + ", tbsIndex = " + tbsIndex + ", dir = " + dir + "]";
    }
}
