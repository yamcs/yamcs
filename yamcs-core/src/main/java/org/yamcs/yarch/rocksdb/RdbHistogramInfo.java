package org.yamcs.yarch.rocksdb;

import org.yamcs.yarch.HistogramInfo;

public class RdbHistogramInfo extends HistogramInfo {
    final int tbsIndex;
    //for time based partitions something like 2017/11
    final String partitionDir;
    public RdbHistogramInfo(int tbsIndex, String columnName, String partitionDir) {
        super(columnName);
        this.tbsIndex = tbsIndex;
        this.partitionDir = partitionDir;
    }
}
