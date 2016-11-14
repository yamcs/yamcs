package org.yamcs.yarch.rocksdb2;

import org.yamcs.yarch.Partition;

public class RdbPartition extends Partition {
    final String dir;
    final byte[] binaryValue;
    
    public RdbPartition(long start, long end, Object value, byte[] binaryValue, String dir) {
        super(start, end, value);
        this.dir = dir;
        this.binaryValue = binaryValue;
    }
}
