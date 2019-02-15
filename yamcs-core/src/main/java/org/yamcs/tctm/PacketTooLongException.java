package org.yamcs.tctm;

/**
 * Exception thrown when a packet is longer than a defined limit
 * @author nm
 *
 */
public class PacketTooLongException extends TcTmException {
    final int maxSize;
    final int actualSize;
    public PacketTooLongException(int maxSize, int actualSize) {
        this.maxSize = maxSize;
        this.actualSize = actualSize;
    }
    
    public String toString() {
        return "PacketTooLongException: packetLength (" + actualSize + ") > maxPacketLength(" + maxSize + ")";
    }
}
