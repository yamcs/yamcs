package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramDb;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.utils.TimeEncoding;

/**
 * 
 * Histogram database
 * @author nm
 *
 */
public class RdbHistogramDb extends HistogramDb {
    YRDB histoDb;

    int nextId=1;
    static Logger log=LoggerFactory.getLogger(RdbHistogramDb.class.getName());
    long lastSync;

    static Map<TableDefinition, RdbHistogramDb> instances=new HashMap<TableDefinition, RdbHistogramDb>();
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

    @Override
    public HistogramIterator getIterator(String colName, TimeInterval interval, long mergeTime) throws IOException {
        try {
            return new RdbHistogramIterator(colName, interval, mergeTime);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
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
                histoDb.put(cfh, Segment.key(Integer.MAX_VALUE, zerobytes), new byte[0]);
            }    		
            histoDb.put(cfh, segkey, segval);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Print the content of the index files
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        if(args.length<2) printUsageAndExit();
        TimeInterval interval = new TimeInterval();
        long mergeTime=2000;
        TimeEncoding.setUp();

        int i;
        for(i=0;i<args.length;i++) {
            if("-s".equals(args[i])) {
                String s=args[++i];
                interval.setStart(TimeEncoding.parse(s));
            } else 	if("-e".equals(args[i])) {
                interval.setStop(TimeEncoding.parse(args[++i]));
            } else 	if("-m".equals(args[i])) {
                mergeTime=Integer.parseInt(args[++i])*1000;
            } else {
                break;
            }
        }
        if(i+2<args.length) printUsageAndExit();
        String dbpath = args[i];
        String colName = args[i+1];



        YarchDatabase ydb=YarchDatabase.getInstance("test");
        RdbHistogramDb index=new RdbHistogramDb(ydb, dbpath, true);
        index.printDb(colName, interval, mergeTime);
    }

    private static void printUsageAndExit() {
        System.err.println("Usage rdbhistogram.sh [-s start_time]  [-e end_time] [-m merge_time_seconds] dbpath colname");
        System.err.println("\t start and end time should be specified like 2009-12-24T09:21:00 or 2009/332T08:33:33");
        System.exit(-1);
    }

    /**
     * 
     * @author nm
     *
     */
    class RdbHistogramIterator extends HistogramIterator {
        RocksIterator it;

        /**
         * 
         * @param interval start,stop         * 
         * @param mergeTime merge records whose stop-start<mergeTime
         * @throws RocksDBException 
         */
        public RdbHistogramIterator(String colName, TimeInterval interval, long mergeTime) throws RocksDBException {
            super(interval, mergeTime);

            ColumnFamilyHandle cfh = histoDb.getColumnFamilyHandle(colName);
            if(cfh==null) {
                finished = true;
            } else {
                it = histoDb.newIterator(cfh);
                if(!interval.hasStart()) {
                    it.seek(Segment.key(0, zerobytes));
                } else {
                    int sstart=(int)(interval.getStart()/groupingFactor);
                    it.seek(Segment.key(sstart, zerobytes));
                }
                
                if(!it.isValid() || !readNextSegments()) {
                    finished=true;
                }
            }
        }

        //reads all the segments with the same sstart time
        protected boolean readNextSegments() {
            ByteBuffer bb = ByteBuffer.wrap(it.key());
            int sstart = bb.getInt();
            if(sstart==Integer.MAX_VALUE) return false;
            records.clear();
            while(true) {
                addRecords(it.key(), it.value());

                it.next();
                if(!it.isValid()) break; 
                bb=ByteBuffer.wrap(it.key());
                int g=bb.getInt();
                if(g!=sstart) break;
            }
            iter=records.iterator();
            return true;
        }       

        public void close() {
            finished=true;
        }
    }
}
