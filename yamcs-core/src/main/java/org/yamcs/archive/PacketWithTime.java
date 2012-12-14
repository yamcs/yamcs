package org.yamcs.archive;

import java.nio.ByteBuffer;

public class PacketWithTime {
    public long rectime;//reception time
    private long gentime; //generation time
    public ByteBuffer bb;

    public PacketWithTime(long rectime, long gentime, ByteBuffer bb) {
        this.rectime = rectime;
        this.gentime = gentime;
        this.bb = bb;
    }

    public long getGenerationTime() {
        return gentime;
    }
}