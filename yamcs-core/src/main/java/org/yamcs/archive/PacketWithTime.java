package org.yamcs.archive;

import org.yamcs.TmPacket;

/**
 * Packet with acquisition time, generation time and sequence count.
 * 
 * It is assumed that (generation time, sequence count) uniquely identifies the packet
 * 
 * @deprecated use {@link TmPacket} instead
 * @author nm
 *
 */
@Deprecated
public class PacketWithTime extends TmPacket {

    public PacketWithTime(long rectime, byte[] pkt) {
        super(rectime, pkt);
    }
    
    public PacketWithTime(long rectime, long gentime, int seqCount, byte[] pkt) {
        super(rectime, gentime, seqCount, pkt);
    }

}
