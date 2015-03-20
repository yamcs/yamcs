package org.yamcs.yarch.tokyocabinet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
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
public class TcHistogramDb extends HistogramDb {
    static Logger log=LoggerFactory.getLogger(TcHistogramDb.class.getName());
    static Map<TableDefinition, TcHistogramDb> instances = new HashMap<TableDefinition, TcHistogramDb>();

    //map from column names to the DB
    Map<String, YBDB>  histoDbs = new HashMap<String, YBDB>();

    int nextId=1;

    long lastSync;

    final YarchDatabase ydb;

    static final byte[] zerobytes=new byte[0];
    String basename;
    /**
     * Open the  histogram db
     * lock is false when called as a standalone program to inspect the index
     * @param file
     * @param lock
     */
    public TcHistogramDb(YarchDatabase ydb, String pathname, boolean readonly) {
	this.ydb = ydb;
	this.basename = pathname;
	lastSync=System.currentTimeMillis();
    }

    private void initDb(YBDB histoDb) throws IOException {
	//add a record at the end to make sure the cursor doesn't run out
	histoDb.put(Segment.key(Integer.MAX_VALUE, zerobytes), new byte[0]);
    }

    @Override
    public HistogramIterator getIterator(String histoColumnName, TimeInterval interval, long mergeTime) throws IOException {
	YBDB db = getDb(histoColumnName, false);
	return new TcHistogramIterator(db, interval, mergeTime);
    }

    @Override
    public void close() throws IOException{
	for(YBDB db: histoDbs.values()) {
	    db.close();
	}
    }

    public static synchronized TcHistogramDb getInstance(YarchDatabase ydb, TableDefinition tbl) {
	TcHistogramDb db=instances.get(tbl);
	if(db==null) {
	    db=new TcHistogramDb(ydb, tbl.getDataDir()+"/"+tbl.getName()+"-histo", false);
	    instances.put(tbl, db);
	}
	return db;
    }

    @Override
    protected byte[] segmentGet(String columnName, byte[] segkey) throws IOException {
	YBDB histoDb = getDb(columnName, false);
	return histoDb.get(segkey);
    }

    @Override
    protected void segmentPut(String columnName, byte[] segkey, byte[] segval) throws IOException {
	YBDB histoDb = getDb(columnName, false); 
	histoDb.put(segkey, segval);
    }

    private synchronized YBDB getDb(String columnName, boolean readonly) throws IOException {
	YBDB histoDb = histoDbs.get(columnName); 
	if(histoDb==null) {
	    histoDb = ydb.getTCBFactory().getTcb(basename+"#"+columnName+".tcb", readonly, false);
	    if(histoDb.rnum()==0) {
		if(readonly) throw new IOException("readonly specified but the index database is not even initialized");
		initDb(histoDb);
	    }
	}
	return histoDb;
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
	TcHistogramDb index=new TcHistogramDb(ydb, dbpath, true);
	index.printDb(colName, interval, mergeTime);
    }

    private static void printUsageAndExit() {
	System.err.println("Usage histogram.sh [-s start_time]  [-e end_time] [-m merge_time_seconds] dbpath column");
	System.err.println("\t start and end time should be specified like 2009-12-24T09:21:00 or 2009/332T08:33:33");
	System.exit(-1);
    }

    /**
     * provides histogram records sorted by start time. Because in the db they are stored in segments which are read
     * one at time, it has to sort them in memory using a TreeSet
     * @author nm
     *
     */
    class TcHistogramIterator extends HistogramIterator {
	YBDBCUR cursor;

	/**
	 * 
	 * @param interval start,stop         * 
	 * @param mergeTime merge records whose stop-start<mergeTime
	 */
	public TcHistogramIterator(YBDB db, TimeInterval interval, long mergeTime) {
	    super(interval, mergeTime);
	    cursor=db.openCursor();
	    if(!interval.hasStart()) {
		cursor.jump(Segment.key(0, zerobytes));
	    } else {
		int sstart=(int)(interval.getStart()/groupingFactor);
		cursor.jump(Segment.key(sstart, zerobytes));
	    }
	    if(!readNextSegments()) {
		finished=true;
	    }
	}

	//reads all the segments with the same sstart time
	protected boolean readNextSegments() {
	    ByteBuffer bb=ByteBuffer.wrap(cursor.key());
	    int sstart=bb.getInt();
	    if(sstart==Integer.MAX_VALUE) return false;
	    records.clear();
	    while(true) {
		addRecords(cursor.key(), cursor.val());
		cursor.next();
		bb=ByteBuffer.wrap(cursor.key());
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
