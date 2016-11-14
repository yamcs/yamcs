package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.HistogramSegment;

/**
 * 
 * Histogram database
 * @author nm
 *
 */
public class RdbHistogramDb {
    YRDB histoDb;

    int nextId=1;
    static Logger log=LoggerFactory.getLogger(RdbHistogramDb.class.getName());
    long lastSync;

    static Map<TableDefinition, RdbHistogramDb> instances = new HashMap<TableDefinition, RdbHistogramDb>();
    final YarchDatabase ydb;
    TableDefinition tblDef;

    static final byte[] zerobytes=new byte[0];
    /**
     * Open the  histogram db
     * readonly is true when called as a standalone program to inspect the index

     * @param tblDef
     * @throws IOException
     */
    public RdbHistogramDb(YarchDatabase ydb, TableDefinition tblDef, boolean readonly) throws IOException {
        this(ydb, tblDef.getDataDir()+"/"+tblDef.getName()+"-histo", readonly);
        this.tblDef = tblDef;
    }

    //    private void initDb() throws IOException {

    //  }

    public RdbHistogramDb(YarchDatabase ydb, String dbpath, boolean readonly) throws IOException {
        this.ydb = ydb;
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        histoDb = rdbFactory.getRdb(dbpath, new ColumnValueSerializer(DataType.STRING), readonly);
        lastSync = System.currentTimeMillis();
    }

    public Iterator<HistogramRecord> getIterator(String colName, TimeInterval interval, long mergeTime) throws IOException {
        try {
            return new RdbHistogramIterator(colName, interval, mergeTime);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    public void close() {
        synchronized(RdbHistogramDb.class) {
            instances.remove(tblDef);
        }
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        rdbFactory.dispose(histoDb);
    }


    public static synchronized RdbHistogramDb getInstance(YarchDatabase ydb, TableDefinition tblDef) throws IOException {
        RdbHistogramDb db = instances.get(tblDef);
        if(db==null) {
            db = new RdbHistogramDb(ydb, tblDef, false);
            instances.put(tblDef, db);
        }
        assert(db.ydb==ydb);

        return db;
    }

    public synchronized void addValue(String columnName, byte[] columnv, long time) throws IOException {
        int sstart=(int)(time/HistogramSegment.GROUPING_FACTOR);
        int dtime=(int)(time%HistogramSegment.GROUPING_FACTOR);

        HistogramSegment segment;
        byte[] val=segmentGet(columnName, HistogramSegment.key(sstart, columnv));
        if(val==null) {
            segment=new HistogramSegment(columnv, sstart);
        } else {
            segment=new HistogramSegment(columnv, sstart, val);
        }

        segment.merge(dtime);
        segmentPut(columnName, segment.key(), segment.val());
    }


    protected byte[] segmentGet(String colName, byte[] segkey) throws IOException {
        try {
            ColumnFamilyHandle cfh = histoDb.getColumnFamilyHandle(colName);
            if(cfh==null) return null;

            return histoDb.get(cfh, segkey);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    protected void segmentPut(String colName, byte[] segkey, byte[] segval) throws IOException {    	
        try {
            ColumnFamilyHandle cfh= histoDb.getColumnFamilyHandle(colName);
            if(cfh==null) {
                cfh = histoDb.createColumnFamily(colName);
                //add a record at the end to make sure the cursor doesn't run out
                histoDb.put(cfh, HistogramSegment.key(Integer.MAX_VALUE, zerobytes), new byte[0]);
            }    		
            histoDb.put(cfh, segkey, segval);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }


    class RdbHistogramIterator implements Iterator<HistogramRecord> {
        RocksIterator it;
        protected boolean finished = false;
        protected Iterator<HistogramRecord> iter;
        protected TreeSet<HistogramRecord> records=new TreeSet<HistogramRecord>();
        protected final TimeInterval interval;
        protected final long mergeTime;

        /**
         * 
         * @param interval start,stop         * 
         * @param mergeTime merge records whose stop-start<mergeTime
         * @param rdbHistogramDb TODO
         * @throws RocksDBException 
         */
        public RdbHistogramIterator(String colName, TimeInterval interval, long mergeTime) throws RocksDBException {
            this.interval = interval;
            this.mergeTime = mergeTime;

            ColumnFamilyHandle cfh = histoDb.getColumnFamilyHandle(colName);
            if(cfh==null) {
                finished = true;
            } else {
                it = histoDb.newIterator(cfh);
                if(!interval.hasStart()) {
                    it.seek(HistogramSegment.key(0, RdbHistogramDb.zerobytes));
                } else {
                    int sstart=(int)(interval.getStart()/HistogramSegment.GROUPING_FACTOR);
                    it.seek(HistogramSegment.key(sstart, RdbHistogramDb.zerobytes));
                }

                if(it.isValid()) {
                    readNextSegments();
                } else {
                    finished=true;
                }
            }
        }

        //reads all the segments with the same sstart time
        protected void readNextSegments() {
            ByteBuffer bb = ByteBuffer.wrap(it.key());
            int sstart = bb.getInt();
            if(sstart==Integer.MAX_VALUE) {
                finished = true;
                return;
            }
            
            if((interval.hasStop()) && (sstart*HistogramSegment.GROUPING_FACTOR>interval.getStop())) {
                finished = true;
                return;
            }
            records.clear();
            while(true) {
                addRecords(it.key(), it.value());

                it.next();
                if(!it.isValid()) break; 
                bb=ByteBuffer.wrap(it.key());
                int g=bb.getInt();
                if(g!=sstart) break;
            }
            iter = records.iterator();
        }       

        public void close() {
            finished=true;
        }

        public boolean hasNext() {
            return (!finished);
        }

        public HistogramRecord next() {
            if(finished) throw new NoSuchElementException();

            HistogramRecord r = iter.next();
            
            if(!iter.hasNext()) readNextSegments();

            return r;
        }



        protected void addRecords(byte[] key, byte[] val) {
            ByteBuffer kbb = ByteBuffer.wrap(key);
            int sstart = kbb.getInt();
            byte[] columnv=new byte[kbb.remaining()];
            kbb.get(columnv);
            
            ByteBuffer vbb=ByteBuffer.wrap(val);
            HistogramRecord r=null;
            while(vbb.hasRemaining()) {
                long start = sstart*HistogramSegment.GROUPING_FACTOR+vbb.getInt();
                long stop = sstart*HistogramSegment.GROUPING_FACTOR+vbb.getInt();  
                int num = vbb.getShort();
                if((interval.hasStart()) && (stop<interval.getStart())) continue;
                if((interval.hasStop()) && (start>interval.getStop())) {
                    return;
                }
                if(r==null) {
                    r=new HistogramRecord(columnv, start, stop, num);
                    
                } else {
                    if(start-r.getStop()<mergeTime) {
                        r = new HistogramRecord(r.getColumnv(), r.getStart(), stop, r.getNumTuples()+num);
                    } else {
                        records.add(r);
                        r=new HistogramRecord(columnv, start, stop, num);
                    }
                }
            }
            records.add(r);
        }
    }


}
