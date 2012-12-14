package org.yamcs.yarch;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.YBDB;
import org.yamcs.yarch.YBDBCUR;

import org.yamcs.utils.StringConvertors;
import org.yamcs.utils.TimeEncoding;

import com.google.common.primitives.UnsignedBytes;

/**
 * 
 * Histogram
 * @author nm
 *
 */
public class HistogramDb {
    YBDB histoDb;

    int nextId=1;
    static Logger log=LoggerFactory.getLogger(HistogramDb.class.getName());
    long lastSync;
    private static long lossTime=1000; //time in milliseconds above which we consider a packet loss
    static int maxInterval=120000; //make two records if the time between packets is more than 2 minutes (because the packets are not very related)
    static long groupingFactor=3600*1000; //has to be less than 2^16 *1000
    static final int REC_SIZE=10; //4 bytes for start and stop, 2 bytes for num
    static Map<String, HistogramDb> instances=new HashMap<String, HistogramDb>();

    static final byte[] zerobytes=new byte[0];
    /**
     * Open the  histogram db
     * lock is false when called as a standalone program to inspect the index
     * @param file
     * @param lock
     * @throws IOException
     */
    HistogramDb(YarchDatabase ydb, String filename, boolean readonly) throws IOException {
        histoDb=ydb.getTCBFactory().getTcb(filename, readonly, false);
        if(histoDb.rnum()==0) {
            if(readonly) throw new IOException("readonly specified but the index database is not even initialized");
            initDb();
        }
        lastSync=System.currentTimeMillis();
    }

    private void initDb() throws IOException {
        //add a record at the end to make sure the cursor doesn't run out
        histoDb.put(Segment.key(Integer.MAX_VALUE, zerobytes), new byte[0]);
    }

    public static synchronized HistogramDb getInstance(YarchDatabase ydb, String filename) throws IOException {
        HistogramDb db=instances.get(filename);
        if(db==null) {
            db=new HistogramDb(ydb, filename, false);
            instances.put(filename, db);
        }
        return db;
    }

    public synchronized void addValue(byte[] columnv, long time) throws IOException {
        int sstart=(int)(time/groupingFactor);
        int dtime=(int)(time%groupingFactor);

        Segment segment;
        byte[] val=histoDb.get(Segment.key(sstart, columnv));
        if(val==null) {
            segment=new Segment(columnv, sstart);
        } else {
            segment=new Segment(columnv, sstart, val);
        }

        segment.merge(dtime);
        histoDb.put(segment.key(), segment.val());

    }


    public void printDb(TimeInterval interval, long mergeTime) {
        String formatt="%-10s  %-30s - %-30s %5s %5s";
        String format= "%-10s  %-30s - %-30s %5d  %3.3f";

        int c=0;
        long t0=System.currentTimeMillis();
        System.out.println(String.format(formatt,"group", "start","stop","nump","freq"));
        HistogramIterator it=new HistogramIterator(interval, mergeTime);

        Record r;
        while((r=it.getNextRecord())!=null) {
            c++;
            float freq=0;
            if(r.start!=r.stop)freq=(1000*(float)(r.num-1)/(float)(r.stop-r.start));
            System.out.println(String.format(format, StringConvertors.arrayToHexString(r.columnv), TimeEncoding.toCombinedFormat(r.start), TimeEncoding.toCombinedFormat(r.stop),r.num,freq));

        }

        long t1=System.currentTimeMillis();
        System.out.println(c+" records read in "+(t1-t0)+" millis");
    }


    /**
     * Print the content of the index files
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        if(args.length<1) printUsageAndExit();
        TimeInterval interval = new TimeInterval();
        long mergeTime=2000;
        TimeEncoding.setUp();

        String filename=null;
        for(int i=0;i<args.length;i++) {
            if("-s".equals(args[i])) {
                String s=args[++i];
                System.out.println("parsing "+s);
                interval.setStart(TimeEncoding.parse(s));
            } else 	if("-e".equals(args[i])) {
                interval.setStop(TimeEncoding.parse(args[++i]));
            } else 	if("-m".equals(args[i])) {
                mergeTime=Integer.parseInt(args[++i])*1000;
            } else {
                filename=args[i];
            }
        }
        if(!(new File(filename).exists())) {
            System.err.println(filename +" does not exist");
            System.exit(-1);
        }

        YarchDatabase ydb=YarchDatabase.getInstance("test");
        HistogramDb index=new HistogramDb(ydb, filename, true);
        index.printDb(interval, mergeTime);
    }

    private static void printUsageAndExit() {
        System.err.println("Usage histogram.sh [-s start_time]  [-e end_time] [-m merge_time_seconds] file");
        System.err.println("\t start and end time should be specified like 2009-12-24T09:21:00 or 2009/332T08:33:33");
        System.exit(-1);
    }

    /**
     * provides histogram records sorted by start time. Because in the db they are stored in segments which are read
     * one at time, it has to sort them in memory using a TreeSet
     * @author nm
     *
     */
    class HistogramIterator {
        TimeInterval interval;
        YBDBCUR cursor;
        private boolean finished=false;
        long mergeTime;
        Iterator<Record> iter;
        TreeSet<Record> records=new TreeSet<Record>();
        
        /**
         * 
         * @param start time in milliseconds
         * @param stop time in milliseconds
         * @param mergeTime merge records whose stop-start<mergeTime
         */
        public HistogramIterator(TimeInterval interval, long mergeTime) {
            this.interval = interval;
            this.mergeTime=mergeTime;
            cursor=histoDb.openCursor();
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
        private boolean readNextSegments() {
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

        private boolean addRecords(byte[] key, byte[] val) {
            ByteBuffer kbb=ByteBuffer.wrap(key);
            int sstart=kbb.getInt();
            byte[] columnv=new byte[kbb.remaining()];
            kbb.get(columnv);
            ByteBuffer vbb=ByteBuffer.wrap(val);
            Record r=null;
            while(vbb.hasRemaining()) {
                long start=sstart*groupingFactor+vbb.getInt();
                long stop=sstart*groupingFactor+vbb.getInt();              
                int num=vbb.getShort();
                if(r==null) {
                    r=new Record(columnv, start, stop, num);
                } else {
                    if(start-r.stop<mergeTime) {
                        r.stop=stop;
                        r.num+=num;
                    } else {
                        records.add(r);
                        r=new Record(columnv, start, stop, num);
                    }
                }
            }
            records.add(r);
            return true;
        }


        public Record getNextRecord() {
            if(finished) return null;
            Record r=null;
            if(!iter.hasNext()) readNextSegments();
            while(iter.hasNext()) {
                r=iter.next();
                if((interval.hasStart()) && (r.stop<interval.getStart())) continue;
                if((interval.hasStop()) && (r.start>interval.getStop())) {
                    finished=true;
                    r=null;
                }
                break;
            }
            return r;
        }

        public void close() {
            finished=true;
        }
    }

    public HistogramIterator getIterator(TimeInterval interval, long mergeTime) {
        return new HistogramIterator(interval, mergeTime);
    }

    public void close() throws IOException{
        histoDb.close();
    }


    /* 
     * keeps all the records in a {@link groupFactor} millisec interval
     * 
     * */
    static class Segment {
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

        byte[] key() {
            return key(sstart, columnv);
        }

        static byte[] key(int sstart, byte[] columnv) {
            ByteBuffer bbk=ByteBuffer.allocate(4+columnv.length);
            bbk.putInt(sstart);
            bbk.put(columnv);
            return bbk.array();
        }

        byte[] val() {
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
        void merge(int dtime1) {
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
     * "Note: this class has a natural ordering that is inconsistent with equals."
     * @author nm
     *
     */
    static class Record implements Comparable<Record>{
        byte[] columnv;
        long start;
        long stop;
        int num;
        static final Comparator<byte[]>comparator=UnsignedBytes.lexicographicalComparator();

        public Record(byte[] columnv, long start, long stop, int num) {
            this.columnv=columnv;
            this.start=start;
            this.stop=stop;
            this.num=num;
        }

        @Override
        public String toString() {
            return String.format("time:(%d,%d), nump: %d", start, stop, num);
        }

        @Override
        public int compareTo(Record p) {
            if(start!=p.start)return Long.signum(start-p.start);
            return comparator.compare(columnv, p.columnv);
        }
    }
}
