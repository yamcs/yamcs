package org.yamcs.tctm;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Fills in the time, seq and checksum
 * 
 * @author nm
 *
 */
public class CcsdsSeqCountFiller {
    static Map<Integer, Integer> seqCounts = new HashMap<Integer, Integer>();

    /**
     * generate a new ccsds primary header sequence count for the given apid
     * 
     * @param apid
     * @return
     */
    private synchronized int getSeqCount(int apid) {
        int seqCount = 0;
        if (seqCounts.containsKey(apid)) {
            seqCount = seqCounts.get(apid);
        }
        seqCount = (seqCount + 1) % (1 << 14);
        seqCounts.put(apid, seqCount);
        return seqCount;
    }

    /**
     * generates a sequence count and fills it in
     * 
     * @param packet
     * @return  returns the generated sequence count
     */
    public int fill(byte[] packet) {
        int apidseqcount = ByteArrayUtils.decodeInt(packet, 0);
        
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seqFlags = apidseqcount >>> 14;
        
        int seqCount = getSeqCount(apid);

        ByteArrayUtils.encodeUnsignedShort((short) ((seqFlags << 14) | seqCount), packet, 2);

        return seqCount;
    }
}
