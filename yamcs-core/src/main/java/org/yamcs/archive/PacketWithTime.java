package org.yamcs.archive;

import org.yamcs.utils.TimeEncoding;

/**
 * Packet with acquisition time, generation time and sequence count.
 * 
 * It is assumed that (generation time, sequence count) uniquely identifies the packet
 * 
 * @author nm
 *
 */
public class PacketWithTime {
    private long rectime = TimeEncoding.INVALID_INSTANT; // reception time
    private long gentime = TimeEncoding.INVALID_INSTANT; // generation time
    private int seqCount;
    private byte[] pkt;
    boolean corrupted = false;

    public PacketWithTime(long rectime, long gentime, int seqCount, byte[] pkt) {
        this.rectime = rectime;
        this.gentime = gentime;
        this.seqCount = seqCount;
        this.pkt = pkt;
    }

    public long getGenerationTime() {
        return gentime;
    }

    public long getReceptionTime() {
        return rectime;
    }

    public int getSeqCount() {
        return seqCount;
    }

    public byte[] getPacket() {
        return pkt;
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    public boolean isCorrupted() {
        return corrupted;
    }
}
