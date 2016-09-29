package org.yamcs.yarch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import org.yamcs.TimeInterval;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Histogram database.
 * Keeps records of the shape (column, start, stop, numTuples) for all columns that are selected at table creation time
 * 
 * Note that currently it is not synchronised perfectly with the table content. If a crash happens after an insert, the histogram database would have not been updated.
 * 
 * @author nm
 *
 */
public abstract class HistogramDb {
    private static long lossTime = 1000; //time in milliseconds above which we consider a packet loss
    static int maxInterval = 120000; //make two records if the time between packets is more than 2 minutes (because the packets are not very related)
    protected static long groupingFactor = 3600*1000; //has to be less than 2^16 *1000
    static final int REC_SIZE = 10; //4 bytes for start and stop, 2 bytes for num


    public abstract HistogramIterator getIterator(String columnName, TimeInterval interval, long mergeTime) throws IOException;
    protected abstract byte[] segmentGet(String columnName, byte[] segkey) throws IOException;
    protected abstract void segmentPut(String columnName, byte[] segkey, byte[] segval) throws IOException;
    public abstract void close() throws IOException;

    public synchronized void addValue(String columnName, byte[] columnv, long time) throws IOException {
	int sstart=(int)(time/groupingFactor);
	int dtime=(int)(time%groupingFactor);

	Segment segment;
	byte[] val=segmentGet(columnName, Segment.key(sstart, columnv));
	if(val==null) {
	    segment=new Segment(columnv, sstart);
	} else {
	    segment=new Segment(columnv, sstart, val);
	}

	segment.merge(dtime);
	segmentPut(columnName, segment.key(), segment.val());
    }

    public void printDb(String columnName, TimeInterval interval, long mergeTime) throws IOException {
	String formatt="%-10s  %-30s - %-30s %5s %5s";
	String format= "%-10s  %-30s - %-30s %5d  %3.3f";

	int c=0;
	long t0=System.currentTimeMillis();
	System.out.println(String.format(formatt,"group", "start","stop","nump","freq"));
	HistogramIterator it = getIterator(columnName, interval, mergeTime);

	HistogramRecord r;
	while((r=it.getNextRecord())!=null) {
	    c++;
	    float freq=0;
	    if(r.getStart()!=r.getStop())freq=(1000*(float)(r.getNumTuples()-1)/(float)(r.getStop()-r.getStart()));
	    System.out.println(String.format(format, StringConverter.arrayToHexString(r.getColumnv()), TimeEncoding.toCombinedFormat(r.getStart()), TimeEncoding.toCombinedFormat(r.getStop()),r.getNumTuples(), freq));

	}

	long t1=System.currentTimeMillis();
	System.out.println(c+" records read in "+(t1-t0)+" millis");
    }



    /* 
     * keeps all the records in a {@link groupFactor} millisec interval
     * 
     * */
    protected static class Segment {
	byte[] columnv;
	int sstart; //segment start 
	ArrayList<SegRecord> pps;
	/**
	 * Constructs an empty segment
	 * @param sstart
	 */
	public Segment(byte[] columnv, int sstart) {
	    this.columnv=columnv;
	    this.sstart=sstart;
	    pps=new ArrayList<SegRecord>();
	}

	public Segment(byte[] columnv, int sstart, byte[] val) {
	    ByteBuffer v=ByteBuffer.wrap(val);
	    this.columnv=columnv;
	    this.sstart=sstart;
	    pps=new ArrayList<SegRecord>();
	    while(v.hasRemaining()) {
		pps.add(new SegRecord(v.getInt(),v.getInt(),v.getShort()));
	    }
	}

	public Segment(byte[] key, byte[] val) {
	    ByteBuffer k=ByteBuffer.wrap(key);
	    ByteBuffer v=ByteBuffer.wrap(val);
	    this.sstart=k.getInt(0);
	    columnv=new byte[k.remaining()];
	    k.get(columnv);
	    pps=new ArrayList<SegRecord>();
	    while(v.hasRemaining()) {
		pps.add(new SegRecord(v.getInt(),v.getInt(),v.getShort()));
	    }
	}

	public byte[] key() {
	    return key(sstart, columnv);
	}

	public static byte[] key(int sstart, byte[] columnv) {
	    ByteBuffer bbk=ByteBuffer.allocate(4+columnv.length);
	    bbk.putInt(sstart);
	    bbk.put(columnv);
	    return bbk.array();
	}

	public byte[] val() {
	    ByteBuffer bbv=ByteBuffer.allocate(REC_SIZE*pps.size());
	    for(SegRecord p:pps) {
		bbv.putInt(p.dstart);
		bbv.putInt(p.dstop);
		bbv.putShort(p.num);
	    }
	    return bbv.array();
	}



	//used for merging - all these should be put into a PpMerger class
	//actions
	private boolean mergeLeft=false, mergeRight=false;
	//results
	boolean duplicate=false, leftUpdated=false, centerAdded=false,  rightUpdated=false, rightDeleted=false;

	int leftIndex=-1, rightIndex=-1;
	SegRecord left, right;
	int dtime;

	/**
	 * @param dtime1 delta time from segment start in milliseconds
	 */
	public void merge(int dtime1) {
	    this.dtime=dtime1;
	    for(int i=0;i<pps.size();i++) {
		SegRecord r=pps.get(i);
		if(dtime>=r.dstart) {
		    if(dtime<=r.dstop) { //inside left
			duplicate=true;
			return;
		    }
		    left=r;
		    leftIndex=i;
		    continue;
		}
		if(dtime<r.dstart) { 
		    rightIndex=i;
		    right=r;
		    break;
		}
	    }

	    if(leftIndex!=-1) checkMergeLeft();

	    if(rightIndex!=-1)checkMergeRight();
	    if(mergeLeft && mergeRight) {
		selectBestMerge();
	    }

	    //based on the information collected above, compute the new records
	    if(mergeLeft & mergeRight) {
		pps.set(leftIndex, new SegRecord(left.dstart,right.dstop,(short)(left.num+right.num+1)));
		pps.remove(rightIndex);
		leftUpdated=true;
		rightDeleted=true;
	    } else if(mergeLeft) {
		left.dstop=dtime;
		left.num++;
		leftUpdated=true;
	    } else if (mergeRight) {
		right.dstart=dtime;
		right.num++;
		rightUpdated=true;
	    } else { //add a new record
		SegRecord center=new SegRecord(dtime, dtime, (short)1);
		if(leftIndex!=-1) {
		    pps.add(leftIndex+1,center);
		} else if(rightIndex!=-1) {
		    pps.add(rightIndex,center);
		} else {
		    pps.add(center);
		}

		centerAdded=true;
	    }
	}


	float leftInterval=-1;
	float rightInterval=-1;


	private void checkMergeLeft() { //check if it can be merged to left
	    if((dtime-left.dstop)<maxInterval) {
		if(left.num==1) {
		    mergeLeft=true;
		} else {
		    leftInterval=(left.dstop-left.dstart)/(left.num-1);
		    if((dtime-left.dstop) < leftInterval+lossTime) {
			mergeLeft=true;
		    }
		}
	    }
	}

	private void checkMergeRight() { //check if it can be merged to right
	    if((right.dstart-dtime)<maxInterval) {
		if(right.num==1) {
		    mergeRight=true;
		} else {
		    rightInterval=(right.dstop-right.dstart)/(right.num-1);
		    if((right.dstart-dtime) < rightInterval+lossTime) {
			mergeRight=true;
		    }           
		}
	    }
	}

	private void selectBestMerge() {
	    int intervalToLeft=dtime-left.dstop;
	    int intervalToRight=right.dstart-dtime;
	    if(Math.abs(intervalToLeft-intervalToRight)>=lossTime) {
		if(intervalToLeft<intervalToRight) {
		    mergeRight=false;
		} else {
		    mergeLeft=false;
		}
	    }
	}
	//add a new record to the segment (to be used for testing only
	void add(int dstart, int dstop, short num) {
	    pps.add(new SegRecord(dstart, dstop, num));

	}

	@Override
	public String toString() {
	    return "start: "+sstart+", columnv: "+new String(columnv)+" recs:"+pps;
	}


	static class SegRecord {
	    int dstart,dstop; //deltas from the segment start in milliseconds
	    short num;
	    public SegRecord(int dstart, int dstop, short num) {
		this.dstart=dstart;
		this.dstop=dstop;
		this.num=num;
	    }

	    @Override
	    public String toString() {
		return String.format("time:(%d,%d), nump: %d",dstart,dstop,num);
	    }
	}
    }


    /**
     * provides histogram records sorted by start time. Because in the db they are stored in segments which are read
     * one at time, it has to sort them in memory using a TreeSet
     * @author nm
     *
     */
    public abstract class HistogramIterator {
	protected boolean finished=false;
	protected Iterator<HistogramRecord> iter;
	protected TreeSet<HistogramRecord> records=new TreeSet<HistogramRecord>();
	protected final TimeInterval interval;
	protected final long mergeTime;

	public HistogramIterator(TimeInterval interval, long mergeTime) {
	    this.interval = interval;
	    this.mergeTime = mergeTime;         
	}


	public HistogramRecord getNextRecord() {
	    if(finished) return null;

	    HistogramRecord r=null;
	    if(!iter.hasNext()) readNextSegments();
	    while(iter.hasNext()) {
		r=iter.next();
		if((interval.hasStart()) && (r.getStop()<interval.getStart())) continue;
		if((interval.hasStop()) && (r.getStart()>interval.getStop())) {
		    finished=true;
		    r=null;
		}
		break;
	    }
	    return r;
	}



	protected boolean addRecords(byte[] key, byte[] val) {
	    ByteBuffer kbb=ByteBuffer.wrap(key);
	    int sstart=kbb.getInt();
	    byte[] columnv=new byte[kbb.remaining()];
	    kbb.get(columnv);
	    ByteBuffer vbb=ByteBuffer.wrap(val);
	    HistogramRecord r=null;
	    while(vbb.hasRemaining()) {
		long start=sstart*groupingFactor+vbb.getInt();
		long stop=sstart*groupingFactor+vbb.getInt();              
		int num=vbb.getShort();
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
	    return true;
	}
	public abstract void close() throws IOException;

	/**
	 * reads next segments and return true if finished
	 * @return
	 */
	protected abstract boolean readNextSegments();
    }
}
