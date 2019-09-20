package org.yamcs.tctm;

import org.yamcs.YConfiguration;
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
        super(yamcsInstance, YConfiguration.emptyConfig());
    }

    @Override
    public PacketWithTime process(byte[] packet) {

        // check that we're processing a CFDP packet by verifying that we're reading
        // a CCSDS packet (that encapsulates the CFDP payload)
        // For this, we read the first 2 byte and assert that they're 0x0ffd or 0x1ffd
        if ((packet[0] & 0xef) != 0x0f || ((packet[1] & 0xff) != 0xfd)) {
            return null;
        }

        int entityIdLength = ((packet[3] >> 4) & 0x07) + 1;
        int sequenceNumberLength = (packet[3] & 0x07) + 1;
        byte[] seqnr = java.util.Arrays.copyOfRange(packet, 4 + entityIdLength,
                4 + entityIdLength + sequenceNumberLength);
        long sequenceNr = CfdpUtils.getUnsignedLongFromByteArray(seqnr);

        return new PacketWithTime(timeService.getMissionTime(), System.currentTimeMillis(), (int) sequenceNr, packet);
    }
}
