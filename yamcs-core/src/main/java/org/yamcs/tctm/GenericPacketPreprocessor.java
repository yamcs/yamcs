package org.yamcs.tctm;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Generic packet preprocessor.
 * <br>
 * Reads the timestamp (8 bytes) and the sequence count (4 bytes) from a user defined offset.
 * <br>
 * Optionally allows to specify also a checksum algorithm to be used. The checksum is at the end of the packet.
 * <br>
 * <table>
 * <tr>
 * <td>timestampOffset</td>
 * <td>Offset in the packet where to read the 8 bytes timestamp from. If negative, do not read the timestmap from within
 * the
 * packet but use the local wallclock time instead</td>
 * </tr>
 * <tr>
 * <td>seqCountOffset</td>
 * <td>Offset in the packet where to read the sequence count from. If negative, do not read the sequence count from
 * within the packet and set it to 0 instead.</td>
 * </tr>
 * <tr>
 * <td>errorDetection</td>
 * <td>If present, specify which error detection to use. Example: errorDetection: <br>&nbsp;&nbsp;-type: "CRC-16-CCIIT"</td>
 * </tr> 
 * 
 * </table>
 * 
 * @author nm
 *
 */
public class GenericPacketPreprocessor extends AbstractPacketPreprocessor {

    // where from the packet to read the 8 bytes timestamp
    final int timestampOffset;

    // where from the packet to read the 4 bytes sequence count
    final int seqCountOffset;

    public GenericPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        timestampOffset = config.getInt("timestampOffset");
        seqCountOffset = config.getInt("seqCountOffset");
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();
        
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
        if (timestampOffset < 0) {
            gentime = TimeEncoding.getWallclockTime();
        } else {
            if (packet.length < timestampOffset + 8) {
                eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, "Packet too short to extract timestamp");
                gentime = -1;
                corrupted = true;
            } else {
                gentime = TimeEncoding.fromUnixMillisec(ByteArrayUtils.decodeLong(packet, timestampOffset));
            }
        }

        int seqCount = 0;
        if (seqCountOffset >= 0) {
            if (packet.length < seqCountOffset + 4) {
                eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, "Packet too short to extract sequence count");
                seqCount = -1;
                corrupted = true;
            } else {
                seqCount = ByteArrayUtils.decodeInt(packet, seqCountOffset);
            }
        }

        tmPacket.setSequenceCount(seqCount);
        tmPacket.setGenerationTime(gentime);
        tmPacket.setInvalid(corrupted);
        return tmPacket;
    }
}
