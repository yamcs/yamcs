package org.yamcs.yarch.oldrocksdb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;

public abstract class AbstractTableWriter extends TableWriter {
    static final byte[] zerobytes = new byte[0];
    
    
    public AbstractTableWriter(YarchDatabaseInstance ydb, TableDefinition tableDefinition, InsertMode mode)  throws FileNotFoundException {
        super(ydb, tableDefinition, mode);
    }

    protected void addHistogram(YRDB db, Tuple t) throws RocksDBException {
        List<String> histoColumns = tableDefinition.getHistogramColumns();
        for(String c: histoColumns) {
            if(!t.hasColumn(c)) continue;
            long time = (Long)t.getColumn(0);
            ColumnSerializer cs = tableDefinition.getColumnSerializer(c);
            byte[] v = cs.toByteArray(t.getColumn(c));
            addHistogramForColumn(db, c, v, time);
        }
    }


    private synchronized void addHistogramForColumn(YRDB db, String columnName, byte[] columnv, long time) throws RocksDBException {
        long sstart = time/HistogramSegment.GROUPING_FACTOR;
        int dtime = (int)(time%HistogramSegment.GROUPING_FACTOR);

        HistogramSegment segment;
        String cfHistoName = getHistogramColumnFamilyName(columnName);
        ColumnFamilyHandle cfh = db.getColumnFamilyHandle(cfHistoName);
        
        if(cfh==null) {
            cfh = db.createColumnFamily(cfHistoName);
            //add a record at the end to make sure the cursor doesn't run out
            db.put(cfh, HistogramSegment.key(Long.MAX_VALUE, zerobytes), new byte[0]);
        }  
        
        byte[] val = db.get(cfh, HistogramSegment.key(sstart, columnv));
        if(val==null) {
            segment = new HistogramSegment(columnv, sstart);
        } else {
            segment = new HistogramSegment(columnv, sstart, val);
        }

        segment.merge(dtime);
        db.put(cfh, segment.key(), segment.val());
    }
    
    static public String getHistogramColumnFamilyName(String tableColumnName) {
        return ("histo-"+tableColumnName).intern();
    }

    public abstract RdbPartition getDbPartition(Tuple tuple) throws IOException ;
    
}
