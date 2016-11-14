package org.yamcs.yarch.rocksdb2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

import static org.yamcs.yarch.rocksdb2.RdbTableWriter.zerobytes;

/**
 * 
 * @author nm
 *
 */
class RdbHistogramIterator implements Iterator<HistogramRecord> {

    private Iterator<List<Partition>> partitionIterator;
    private RocksIterator segmentIterator;

    private PriorityQueue<HistogramRecord> records = new PriorityQueue<HistogramRecord>();

    private final TimeInterval interval;
    private final long mergeTime;

    YarchDatabase ydb;
    TableDefinition tblDef;
    YRDB rdb;

    Logger log;
    String colName;


    //FIXME: mergeTime does not merge records across partitions or segments
    public RdbHistogramIterator(YarchDatabase ydb, TableDefinition tblDef, String colName, TimeInterval interval, long mergeTime) throws RocksDBException {
        this.interval = interval;
        this.mergeTime = mergeTime;
        this.ydb = ydb;
        this.tblDef = tblDef;
        this.colName = colName;

        PartitionManager partMgr = RdbStorageEngine.getInstance(ydb).getPartitionManager(tblDef);
        partitionIterator = partMgr.iterator(interval.getStart(), null);
        log = LoggerFactory.getLogger(this.getClass().getName()+"["+tblDef.getName()+"]");
        readNextPartition();
    }

    private void readNextPartition() {
        try {
            while(partitionIterator.hasNext()) {
                RdbPartition part  = (RdbPartition) partitionIterator.next().get(0);
                if(interval.hasStop() && part.getStart()>interval.getStop()) {
                    break; //finished
                }
                RDBFactory rdbf = RDBFactory.getInstance(ydb.getName());
                String dbDir = part.dir;

                log.debug("opening database "+ dbDir);
                if(rdb!=null) {
                    rdbf.dispose(rdb);
                }
                rdb = rdbf.getRdb(tblDef.getDataDir()+"/"+dbDir, false);
                String histoCfName = RdbTableWriter.getHistogramColumnFamilyName(colName);
                ColumnFamilyHandle cfh = rdb.getColumnFamilyHandle(histoCfName);
                if(cfh!=null) {
                    segmentIterator = rdb.newIterator(cfh);
                    if(!interval.hasStart()) {
                        segmentIterator.seek(HistogramSegment.key(0, zerobytes));
                    } else {
                        int sstart=(int)(interval.getStart()/HistogramSegment.GROUPING_FACTOR);
                        segmentIterator.seek(HistogramSegment.key(sstart, zerobytes));
                    }

                    if(segmentIterator.isValid()) {
                        readNextSegments();
                        break;
                    }
                }
                rdbf.dispose(rdb);
            }
        } catch (IOException e) {
            log.error("Failed to open database", e);
        }
    }

    //reads all the segments with the same sstart time
    private void readNextSegments() {
        ByteBuffer bb = ByteBuffer.wrap(segmentIterator.key());
        int sstart = bb.getInt();
        if(sstart==Integer.MAX_VALUE) {
            readNextPartition();
            return;
        }
        
        while(true) {
            if(!addRecords(segmentIterator.key(), segmentIterator.value())){
                break;
            }
            segmentIterator.next();
            if(!segmentIterator.isValid()) {
                readNextPartition();
                break;
            }
            bb = ByteBuffer.wrap(segmentIterator.key());
            int g = bb.getInt();
            if(g!=sstart) break;
        }
    }       

    public void close() {
        if(rdb!=null) {
            RDBFactory.getInstance(ydb.getName()).dispose(rdb);
            rdb = null;
        }
    }

    private boolean addRecords(byte[] key, byte[] val) {
        ByteBuffer kbb = ByteBuffer.wrap(key);
        int sstart = kbb.getInt();
        byte[] columnv = new byte[kbb.remaining()];
        kbb.get(columnv);
        ByteBuffer vbb=ByteBuffer.wrap(val);
        HistogramRecord r = null;
        while(vbb.hasRemaining()) {
            long start = sstart*HistogramSegment.GROUPING_FACTOR + vbb.getInt();
            long stop = sstart*HistogramSegment.GROUPING_FACTOR + vbb.getInt();              
            int num = vbb.getShort();
            if((interval.hasStart()) && (stop<interval.getStart())) continue;
            
            if((interval.hasStop()) && (start>interval.getStop())) {
                return false;
            }
            if(r==null) {
                r = new HistogramRecord(columnv, start, stop, num);
            } else {
                if(start-r.getStop()<mergeTime) {
                    r = new HistogramRecord(r.getColumnv(), r.getStart(), stop, r.getNumTuples()+num);
                } else {
                    records.add(r);
                    r = new HistogramRecord(columnv, start, stop, num);
                }
            }
        }
        records.add(r);
        return true;
    }

    public boolean hasNext() {
        return !records.isEmpty();
    }

    public HistogramRecord next() {
        if(records.isEmpty()) throw new NoSuchElementException();
        HistogramRecord r = records.poll();

        if(records.isEmpty()) {
            readNextSegments();
        }
        System.out.println("r: "+r);
        return r;
    }
}