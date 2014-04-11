package org.yamcs.archive;

public class PacketWithTime {
    public long rectime;//reception time
    private long gentime; //generation time
    private byte[] pkt;

    public PacketWithTime(long rectime, long gentime, byte[] pkt) {
        this.rectime = rectime;
        this.gentime = gentime;
        this.pkt = pkt;
    }

    public long getGenerationTime() {
        return gentime;
    }
    
    public byte[] getPacket() {
        return pkt;
    }
}