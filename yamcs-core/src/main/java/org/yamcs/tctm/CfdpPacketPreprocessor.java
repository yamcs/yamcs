package org.yamcs.tctm;

import org.yamcs.archive.PacketWithTime;
import org.yamcs.utils.CfdpUtils;

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
        int entityIdLength = ((packet[3] >> 4) & 0x07) + 1;
        int sequenceNumberLength = (packet[3] & 0x07) + 1;
        byte[] seqnr = java.util.Arrays.copyOfRange(packet, 4 + entityIdLength,
                4 + entityIdLength + sequenceNumberLength);
        long sequenceNr = CfdpUtils.getUnsignedLongFromByteArray(seqnr);

        return new PacketWithTime(timeService.getMissionTime(), System.currentTimeMillis(), (int) sequenceNr, packet);
    }
}
