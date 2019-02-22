package org.yamcs.tctm;

import java.util.Arrays;

import org.yamcs.archive.PacketWithTime;

/**
 * CFDP packet preprocessor.
 *
 * @author ddw
 *
 */
public class CfdpPacketPreprocessor extends AbstractPacketPreprocessor {

    public CfdpPacketPreprocessor(String yamcsInstance) {
        super(yamcsInstance, null);
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        int entityIdLength = (packet[3] >> 4) & 0x07;
        int sequenceNumberLength = packet[3] & 0x07;
        byte[] seqnr = java.util.Arrays.copyOfRange(packet, 4 + entityIdLength,
                4 + entityIdLength + sequenceNumberLength);
        long sequenceNr = Long.parseUnsignedLong(Arrays.toString(seqnr), 16);

        return new PacketWithTime(timeService.getMissionTime(), System.currentTimeMillis(), sequenceNr, packet);
    }
}
