package org.yamcs.archive;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.NotThreadSafe;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.tokyocabinet.TCBFactory;
import org.yamcs.yarch.tokyocabinet.YBDB;
import org.yamcs.yarch.tokyocabinet.YBDBCUR;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.CcsdsPacket;

import tokyocabinet.BDBCUR;

/**
 * Completeness index of CCSDS telemetry. There is one tcb table:
 *   key: apid[2bytes], start time[8 bytes]
 *   value: end time[8bytes], start seq count[2 bytes], end seq count[2 bytes], num packets [4 bytes]
 *   
 *   
 *   The table allows duplicate keys such that it works with multiple packets having the same timestamp
 * @author nm
 *
 */
@ThreadSafe
public class CccsdsTmIndex implements TmIndex {
	
    final protected Logger log;
	
	static long maxApidInterval=3600*1000; //if time between two packets with the same apid is more than one hour, make two records even if they packets are in sequence (because maybe there is a wrap around involved)
	private boolean closed=false;
	private YBDB db;
	private YBDBCUR cursor;
	private long lastSync;
	
	/**
	 * Open the tmindex
	 * if readonly is specified, it is open only for reading and no streams are subscribed
	 * @param fileprefix
	 * @param lock
	 * @throws IOException
	 */
	public CccsdsTmIndex(String instance, boolean readonly) throws IOException, ConfigurationException {
	    log=LoggerFactory.getLogger(this.getClass().getName()+"["+instance+"]");
	    
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
	    
	    db=new YBDB();
		String filename=ydb.getRoot()+"/tmindex.bdb";
		db=ydb.getTCBFactory().getTcb(filename, false, readonly);
		log.info("opened "+filename+" with "+db.rnum()+" records");
		if(db.rnum()==0) initDbs();
		cursor=db.openCursor();
		lastSync=System.currentTimeMillis();
	}
	
	/**Used when started standalone*/
	public CccsdsTmIndex(String filename) throws IOException, ConfigurationException {
	    log=LoggerFactory.getLogger(this.getClass().getName());

	    db=new YBDB();
	    db=TCBFactory.getInstance().getTcb(filename, false, true);
	    log.info("opened "+filename+" with "+db.rnum()+" records");
	    if(db.rnum()==0) initDbs();
	    cursor=db.openCursor();
	    lastSync=System.currentTimeMillis();
	}

	private void initDbs() throws IOException {
		//add a record at the beginning and end to make sure the cursor doesn't run out
		writeHeader();
		db.put(Record.key(Short.MAX_VALUE,Long.MAX_VALUE), new byte[Record.__val_size]);
	}
	
	@Override
    public void onTuple(Stream stream, Tuple tuple) {
		byte[] packet=(byte[])tuple.getColumn("packet");
        ByteBuffer bbpacket=ByteBuffer.wrap(packet);
        
        short apid= CcsdsPacket.getAPID(bbpacket);
        long time=CcsdsPacket.getInstant(bbpacket);
        short seq=CcsdsPacket.getSequenceCount(bbpacket);
        try {
			addPacket(apid, time , seq);
		} catch (IOException e) {
			log.error("got exception while saving the packet into index");
			e.printStackTrace();
		}
    }
	
	public synchronized void addPacket(short apid, long instant, short seq) throws IOException {
		cursor.jump(Record.key(apid, instant));
		//go to the right till we find a record bigger than the packet
		int cright,cleft;
		Record rright,rleft;
		while(true) {
			rright=new Record(cursor.key(),cursor.val());
			cright=compare(apid,instant,seq, rright);
			//System.out.println("compare of "+apid+","+time+","+seq+" with "+rright+" is "+cright);
			if(cright==0) { //duplicate packet
				if(log.isTraceEnabled()) log.trace("ignored duplicate packet: apid="+apid+" time="+TimeEncoding.toOrdinalDateTime(instant)+" seq="+seq);
				return;
			} else if(cright<0) {
				break;
			} else {
				cursor.next();
			}
		}
		cursor.prev();
		rleft=new Record(cursor.key(),cursor.val());
		cleft=compare(apid,instant,seq,rleft);
	//	System.out.println("arleft="+arleft+"\narright="+arright);
	//	System.out.println("cleft="+cleft+"\ncright="+cright);
		if(cleft==0) {//duplicate packet
		    if(log.isTraceEnabled()) log.trace("ignored duplicate packet: apid="+apid+" time="+TimeEncoding.toOrdinalDateTime(instant)+" seq="+seq);
			return;
		}
		//the cursor is located on the left record and we have a few cases to examine
		if ((cleft==1) && (cright==-1)) { //left and right have to be merged
		    rleft.seqLast=rright.seqLast;
	        rleft.lastTime=rright.lastTime;
	        rleft.numPackets+=rright.numPackets+1;
			cursor.put(rleft.val(), BDBCUR.CPCURRENT);
			cursor.next();
			cursor.out(); //remove the right record
		} else if(cleft==1) {//attach to left
			rleft.seqLast=seq;
			rleft.lastTime=instant;
			rleft.numPackets++;
			cursor.put(rleft.val(), BDBCUR.CPCURRENT);
		} else if(cright==-1) {//attach to right
		    long rstarttime=rright.firstTime();
			rright.seqFirst=seq;
			rright.firstTime=instant;
			rright.numPackets++;
	        
			if(rstarttime==instant) {//if the times are equal, we keep the key replacing the value
				cursor.next();
				cursor.put(rright.val(), BDBCUR.CPCURRENT);
			} else if(rleft.firstTime()==instant){ //add a new record just after the left and remove the right record
				cursor.put(rright.val(), BDBCUR.CPAFTER);
				cursor.next(); cursor.out();
			} else {//remove the record and add a new key
				cursor.next();cursor.out();
				db.put(rright.key(),rright.val());
			}
		} else { //create a new record
			Record r=new Record(apid, instant, seq, 1);
			if((rright.apid()==apid) && (rright.firstTime()==instant)) {// add a record before the right
				cursor.next();
				cursor.put(r.val(), BDBCUR.CPBEFORE);
			} else if((rleft.apid()==apid) && (rleft.firstTime()==instant)) {//so add a record before the left
				cursor.put(r.val(), BDBCUR.CPAFTER);
			} else {//completely new key
				db.put(r.key(),r.val());
			}
		}
		long t=System.currentTimeMillis();
		if(t-lastSync>=60000) {
			db.sync();
			lastSync=t;
		}
	}
	/**compare the packet with the record. 
	 * returns:
	 *  <-1 packet fits at the right and is not attached
	 *  -1 packet fits at the right and is attached
	 *  0 packet fits inside
	 *  1 packet fits at the left and is attached
	 *  >1 packet fits at the right and is not attached
	 */  
	private static int compare(short apid, long time, short seq, Record ar) {
		short arapid=ar.apid();
		if(apid!=arapid) {
			return 0x3FFF*Integer.signum(apid-arapid);
		} 
		int c=compare(time,seq ,ar.firstTime(),ar.firstSeq());
		if(c<=0)return c;
		c=compare(time,seq,ar.lastTime(),ar.lastSeq()) ;
		if(c>=0)return c;
		return 0;
	}
	/**
	 * Compares two packets (assuming apid is the same) and returns the same thing like the function above
	 * @param time1
	 * @param seq1
	 * @param time2
	 * @param seq2
	 * @return
	 */
	static int compare(long time1, short seq1, long time2, short seq2) {
		if(time1<time2) {
			if(((time2-time1)<=maxApidInterval) && (((seq2-seq1) & 0x3FFF)==1))return -1;
			else return -0x3FFF;
		} else if(time1==time2) {
			int d=(seq1-seq2)&0x3FFF;
			if(d<0x2000) return d;
			return d-0x4000;
		} else {
			if(((time1-time2)<=maxApidInterval) && (((seq1-seq2) & 0x3FFF)==1)) return 1;
			else return 0x3FFF;
		}
	}
	/**
	 * add a record at the beginning in order to make sure the cursor doesn't run out. 
	 */
	private void writeHeader() throws IOException {
		db.put(Record.key((short)0, (long)0), new byte[Record.__val_size]);
	}
	
	/* (non-Javadoc)
     * @see org.yamcs.yarch.usoc.TmIndex#close()
     */
	@Override
    public synchronized void close() throws IOException {
	    new Exception().printStackTrace();
	    if(closed)return;
		log.info("Closing the tmindexdb");
		db.close();
		closed=true;
	}
	
	/* (non-Javadoc)
     * @see org.yamcs.yarch.usoc.TmIndex#deleteRecords(long, long)
     */
	@Override
    public synchronized void deleteRecords(long start, long stop) {
		//TODO
	}
	
	public void printApidDb() {
		printApidDb((short)-1,-1L,-1L);
	}
	
	
	private void printApidDb(short apid, long start, long stop, YBDBCUR cur) {
		//System.out.println("apid="+apid+" start="+new Date(start)+"stop="+stop);
		String format="%-10d  %-30s - %-30s  %12d - %12d";
		Record ar;
		if(start!=-1) {
			cur.jump(Record.key(apid, start));
			cur.prev();
			ar=new Record(cur.key(),cur.val());
			if((ar.apid()!=apid)||(ar.lastTime()<start)) cur.next();
		} else {
			cur.jump(Record.key(apid, (short)0));
		}
		
		while(true) {
			ar=new Record(cur.key(),cur.val());
			if(ar.apid()!=apid)break;
			if((stop!=-1)&& (ar.firstTime()>stop)) break;
			System.out.println(String.format(format, ar.apid(),TimeEncoding.toOrdinalDateTime(ar.firstTime()), TimeEncoding.toOrdinalDateTime(ar.lastTime()),ar.firstSeq(),ar.lastSeq()));
			cur.next();
		}
	}
	
	public void printApidDb(short apid, long start, long stop) {
		String formatt="%-10s  %-30s - %-30s  %12s - %12s";
		System.out.println(String.format(formatt,"apid","start","stop","startseq", "stopseq"));
		YBDBCUR cur=db.openCursor();
		Record ar;
		if(apid!=-1) {
			printApidDb(apid, start, stop, cur);
		} else {
			apid=0;
			while(true) {//loop through apids
				cur.jump(Record.key(apid, Long.MAX_VALUE));
				ar=new Record(cur.key(),cur.val());
				apid=ar.apid();
				if(apid==Short.MAX_VALUE) break;
				printApidDb(ar.apid(),start,stop,cur);
			}
		} 
	}
		
	/**
	 * Print the content of the index files
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception{
		if(args.length<1) printUsageAndExit();
		YConfiguration.setup();
		
		long start=-1;
		long stop=-1;
		short apid=-1;
		String filename=null;
		for(int i=0;i<args.length;i++) {
			if("-s".equals(args[i])) {
				start=TimeEncoding.parse(args[++i]);
			} else 	if("-e".equals(args[i])) {
				stop=TimeEncoding.parse(args[++i]);
			} else if("-a".equals(args[i])) {
				apid=Short.parseShort(args[++i]);
			} else {
				filename=args[i];
			}
		}
		
		File f=(new File(filename));
		if(!f.exists()) { 
			System.err.println("File does not exist: "+f);
			System.exit(-1);
		}
		CccsdsTmIndex index=new CccsdsTmIndex(filename);
		index.printApidDb(apid, start, stop);
	}

	private static void printUsageAndExit() {
		System.err.println("Usage print-tm-index.sh [-s start_time]  [-e end_time] [-a apid] file");
		System.err.println("\t start and end time should be specified like 2009-12-34T09:21:00 or 2009/332T08:33:33");
		System.exit(-1);
	}
	
	
	class CcsdsIndexIteratorAdapter implements IndexIterator {
	    CcsdsIndexIterator iterator;
	    final Set<Integer> apids;
	    
        CcsdsIndexIteratorAdapter(long start, long stop) {
            apids=null;
            iterator=new CcsdsIndexIterator((short)-1, start, stop);
        }
        
        @Override
        public void close() {
            iterator.close();
        }

        @Override
        public ArchiveRecord getNextRecord() {
            while(true) {
                Record r=iterator.getNextRecord();
                if(r==null) return null;

                int apid=r.apid;
                if((apids==null) || (apids.contains(apid))) {
                    String pn="apid_"+apid;
                    NamedObjectId id=NamedObjectId.newBuilder().setName(pn).build();
                    ArchiveRecord.Builder arb=ArchiveRecord.newBuilder().setId(id).setNum(r.numPackets)
                        .setFirst(r.firstTime()).setLast(r.lastTime());
                    //WARN: this string is parsed in the CompletenessGUI 
                    //TODO: make it smarter
                    arb.setInfo("seqFirst: "+r.seqFirst+" seqLast: "+r.seqLast);
                    return arb.build();
                }
            }
        }
	    
	}
	@NotThreadSafe
	class CcsdsIndexIterator {
		long start,stop;
		YBDBCUR cur;
		short apid, curApid;
		Record curr;
		
		public CcsdsIndexIterator(short apid, long start, long stop) {
			if(start<0)start=-1;
			if(stop<0)stop=-1;
			this.apid=apid;
			this.start=start;
			this.stop=stop;
			cur=db.openCursor();
			curApid=-1;
			curr=null;
			
		}
		
		
		
		//jumps to the beginning of the curApid returning true if there is any record matching the start criteria
		// and false otherwise
		boolean jumpAtApid() {
			Record r;
			if(start!=-1) {
				cur.jump(Record.key(curApid, start)); cur.prev();
				r=new Record(cur.key(),cur.val());
				if((r.apid()!=curApid) || r.lastTime()<start) cur.next();
				r=new Record(cur.key(),cur.val());
				if(r.apid()!=curApid) return false;
			} else {
				cur.jump(Record.key(curApid,0));
				r=new Record(cur.key(),cur.val());
				if(r.apid()!=curApid) return false;
			}
			return true;
		}
		
		//sets the position of the acur at the beginning of the next apid which matches the start criteria
		boolean nextApid() {
//			System.out.println("apid="+apid+", curtApid="+curApid);
			if(curApid==-1) { //init
				if(apid!=-1) {
					curApid=apid;
					return jumpAtApid();
				} else {
					curApid=0;
				}
			}
			if(apid!=-1)return false;
			
			while(true) {
				cur.jump(Record.key(curApid, Long.MAX_VALUE));
				Record ar=new Record(cur.key(),cur.val());
				curApid=ar.apid();
				if(curApid==Short.MAX_VALUE) return false;

				if(jumpAtApid())return true;
			}
		}
		
		//sets the position of the acur to the next id checking for the stop criteria
		private boolean nextId() {
			if(curr==null) { //init
				if(!nextApid()) return false;
			}  else {
				cur.next();
			}
			while(true) {
				Record r=new Record(cur.key(),cur.val());
				if(r.apid()!=curApid) {
					if(!nextApid()) return false;
					continue;
				}
				if((stop!=-1)&&(r.firstTime()>stop)) {
					if(!nextApid()) return false;
					continue;
				}
				curr=r;
				return true;
			}
		}
	
		/* (non-Javadoc)
         * @see org.yamcs.yarch.usoc.IndexIterator#getNextRecord()
         */
		public Record getNextRecord() {
			//	System.out.println("getNextRecord curId:"+curId+", start:"+start+", apid:"+apid+", stop:"+stop);
		    if(!nextId()) return null;
			return curr;	
		}

		/* (non-Javadoc)
         * @see org.yamcs.yarch.usoc.IndexIterator#close()
         */
		public void close() {
			cur.close();
		}
	}

	/* (non-Javadoc)
     * @see org.yamcs.yarch.usoc.TmIndex#getIterator(short, long, long)
     * names is ignored for the moment
     */
	@Override
    public IndexIterator getIterator(List<NamedObjectId> names, long start, long stop) {
		return new CcsdsIndexIteratorAdapter(start, stop);
	}

    

    @Override
    public void streamClosed(Stream stream) {
    }
}

class Record {
	long firstTime, lastTime;
	short apid;
	short seqFirst, seqLast;
	int numPackets;
	static final int __key_size=10;
	static final int __val_size=16;
	
	public Record(byte[] key, byte[] val) {
		ByteBuffer keyb=ByteBuffer.wrap(key);
		ByteBuffer valb=ByteBuffer.wrap(val);
		apid=keyb.getShort();
		firstTime=keyb.getLong();
		lastTime=valb.getLong();
		seqFirst=valb.getShort();
		seqLast=valb.getShort();
		numPackets=valb.getInt();
	}
	
	
	public Record(short apid, long time, short seq, int numPackets) {
		this.apid=apid;
		this.firstTime=time;
		this.lastTime=time;
		this.seqFirst=seq;
		this.seqLast=seq;
		this.numPackets=numPackets;
	}


	static byte[] key(short apid, long start) {
		ByteBuffer bbk=ByteBuffer.allocate(__key_size);
		bbk.putShort(apid);
		bbk.putLong(start);
		return bbk.array();
	}


	public long firstTime() {
		return firstTime;
	}
	public long lastTime() {
		return lastTime;
	}
	public short apid() {
		return apid;
	}
	public short firstSeq() {
		return seqFirst;
	}
	public short lastSeq() {
		return seqLast;
	}
	
	byte[] key() {
		ByteBuffer bbk=ByteBuffer.allocate(__key_size);
		bbk.putShort(apid);
		bbk.putLong(firstTime);
		return bbk.array();
	}
	byte[] val() {
		ByteBuffer bbv=ByteBuffer.allocate(__val_size);
		bbv.putLong(lastTime);
		bbv.putShort(seqFirst);
		bbv.putShort(seqLast);
	    bbv.putInt(numPackets);
		return bbv.array();
	}
	
	@Override
    public String toString() {
	    return "apid="+apid()+" time: ("+firstTime+","+lastTime+") seq:("+firstSeq()+","+lastSeq()+")";
	}


    public int numPackets() {
        return numPackets;
    }	
}