package org.yamcs.tctm;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.TimeEncoding;

/**
 * Fills in the time, seq and checksum 
 * @author nm
 *
 */
public class CcsdsSeqAndChecksumFiller {
	static Map<Integer,Integer> seqCounts=new HashMap<Integer,Integer>();
	
	/**
	 * generate a new ccsds primary header sequence count for the given apid
	 * @param apid
	 * @return
	 */
	private synchronized int getSeqCount(int apid) {
		int seqCount=0;
		if(seqCounts.containsKey(apid)) {
			seqCount=seqCounts.get(apid);
		}
		seqCount=(seqCount+1)%(1<<14);
		seqCounts.put(apid, seqCount);
		return seqCount;
	}
	
	/**
	 * generates a sequence count and fills it in plus the checksum and the generation time
	 * returns the generated sequence count
	 * @param bb
	 * @param genTime 
	 */
	public int fill(ByteBuffer bb, long genTime) {
		int apid=bb.getShort(0)&0x07FF;
		int seqCount=getSeqCount(apid);
		int seqFlags=bb.getShort(2)>>>14;
		bb.putShort(2,(short)((seqFlags<<14)|seqCount));
		
		GpsCcsdsTime gpsTime = TimeEncoding.toGpsTime(genTime);
		bb.putInt(6, gpsTime.coarseTime);
		bb.put(10, gpsTime.fineTime);
		
		
		int checksum=0;
		int l=bb.capacity();
		for(int i=0;i<l-2;i+=2) {
			checksum+=bb.getShort(i);
		}
		bb.putShort(l-2,(short)(checksum&0xFFFF));
		
		return seqCount;
	}
}
