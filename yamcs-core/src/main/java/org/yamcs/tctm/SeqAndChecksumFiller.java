package org.yamcs.tctm;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class SeqAndChecksumFiller {
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
	 * generates a sequence count and fills it in plus the checksum
	 * returns the generated sequence count
	 * @param bb
	 */
	public int fill(ByteBuffer bb) {
		int apid=bb.getShort(0)&0x07FF;
		int seqCount=getSeqCount(apid);
		int seqFlags=bb.getShort(2)>>>14;
		bb.putShort(2,(short)((seqFlags<<14)|seqCount));
		int checksum=0;
		int l=bb.capacity();
		for(int i=0;i<l-2;i+=2) {
			checksum+=bb.getShort(i);
		}
		bb.putShort(l-2,(short)(checksum&0xFFFF));
		
		return seqCount;
	}
}
