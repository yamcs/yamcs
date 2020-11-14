package org.yamcs.yarch.rocksdb;

import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.HistogramInfo;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.*;

public class RdbHistogramInfo extends HistogramInfo {
   
    final int tbsIndex;
    //for time based partitions something like 2017/11
    final String partitionDir;
    public RdbHistogramInfo(int tbsIndex, String columnName, String partitionDir) {
        super(columnName);
        this.tbsIndex = tbsIndex;
        this.partitionDir = partitionDir;
    }
    
    
    public static byte[] histoDbKey(int tbsIndex, long sstart, byte[] columnv) {
        byte[] dbKey = new byte[TBS_INDEX_SIZE + 8 + columnv.length];
        ByteArrayUtils.encodeInt(tbsIndex, dbKey , 0);
        ByteArrayUtils.encodeLong(sstart, dbKey, TBS_INDEX_SIZE);
        System.arraycopy(columnv, 0, dbKey, TBS_INDEX_SIZE + 8, columnv.length);
        
        return dbKey;
    }
    
    @Override
    public String toString() {
        return "RdbHistogramInfo [tbsIndex=" + tbsIndex + ", partitionDir=" + partitionDir + ", columnName="
                + columnName + "]";
    }
}
