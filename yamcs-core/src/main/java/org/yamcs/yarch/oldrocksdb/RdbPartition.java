package org.yamcs.yarch.oldrocksdb;

import org.yamcs.yarch.Partition;

public class RdbPartition extends Partition {
    final String dir;
    final byte[] binaryValue;
    
    public RdbPartition(long start, long end, Object v, byte[] binaryValue, String dir) {
        super(start, end, v);
        this.dir = dir;
        this.binaryValue = binaryValue;
    }
    public String getDir() {
        return dir;
    }
}
