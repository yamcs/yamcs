package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Generic packet preprocessor.
 * <br>
 * Reads the timestamp (8 bytes) and the sequence count (4 bytes) from a user defined offset.
 * <br>
 * Optionally allows to specify also a checksum algorithm to be used. The checksum is at the end of the packet.
 * <br>
 * 
 * @author nm
 *
 */
public class GenericPacketPreprocessor extends AbstractPacketPreprocessor {

    // where from the packet to read the 8 bytes timestamp
    final int timestampOffset;

    // where from the packet to read the 4 bytes sequence count
    final int seqCountOffset;

    public GenericPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        super(yamcsInstance, config);
        timestampOffset = YConfiguration.getInt(config, "timestampOffset");
        seqCountOffset = YConfiguration.getInt(config, "seqCountOffset");
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        boolean corrupted = false;
        if (errorDetectionCalculator != null) {
            int computedCheckword;
            try {
                int n = packet.length;
                computedCheckword = errorDetectionCalculator.compute(packet, 0, n - 2);
                int packetCheckword = ByteArrayUtils.decodeShort(packet, n - 2);
                if (packetCheckword != computedCheckword) {
                    eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET,
                            "Corrupted packet received, computed checkword: " + computedCheckword
                                    + "; packet checkword: " + packetCheckword);
                    corrupted = true;
                }
            } catch (IllegalArgumentException e) {
                eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET,
                        "Error when computing checkword: " + e.getMessage());
                corrupted = true;
            }
        }
        long gentime;
        if (packet.length < timestampOffset + 8) {
            eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, "Packet too short to extract timestamp");
            gentime = -1;
            corrupted = true;
        } else {
            gentime = TimeEncoding.fromUnixMillisec(ByteArrayUtils.decodeLong(packet, timestampOffset));
        }

        int seqCount;
        if (packet.length < seqCountOffset + 4) {
            eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, "Packet too short to extract sequence count");
            seqCount = -1;
            corrupted = true;
        } else {
            seqCount = ByteArrayUtils.decodeInt(packet, timestampOffset);
        }

        PacketWithTime pwt = new PacketWithTime(timeService.getMissionTime(), gentime, seqCount, packet);
        pwt.setCorrupted(corrupted);
        return pwt;
    }
}
