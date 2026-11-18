package org.yamcs.examples.hires;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Packet preprocessor that extracts sub-millisecond generation time from
 * the hires simulator packet format.
 *
 * <p>
 * Expected packet layout (after the 2-byte length field has been stripped
 * by {@code GenericPacketInputStream}):
 * <table>
 * <tr><th>Offset</th><th>Size</th><th>Field</th></tr>
 * <tr><td>0</td><td>8</td><td>Generation time: milliseconds (TAI epoch)</td></tr>
 * <tr><td>8</td><td>4</td><td>Generation time: sub-ms picoseconds</td></tr>
 * <tr><td>12</td><td>4</td><td>Sequence count</td></tr>
 * <tr><td>16</td><td>N</td><td>Parameter data</td></tr>
 * </table>
 *
 * <p>
 * Note: offsets are relative to the packet bytes received by this preprocessor,
 * which do NOT include the 2-byte length prefix (it is consumed by
 * GenericPacketInputStream).
 */
public class HiresPacketPreprocessor extends AbstractPacketPreprocessor {

    public HiresPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public HiresPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();

        if (packet.length < 16) {
            eventProducer.sendWarning("Packet too short: " + packet.length + " bytes");
            tmPacket.setInvalid(true);
            return tmPacket;
        }

        // Extract 8-byte TAI milliseconds at offset 0
        long millis = ByteArrayUtils.decodeLong(packet, 0);

        // Extract 4-byte sub-millisecond picoseconds at offset 8
        int picos = ByteArrayUtils.decodeInt(packet, 8);

        // Set generation time with full picosecond precision
        tmPacket.setGenerationTime(Instant.get(millis, picos));

        // Extract 4-byte sequence count at offset 12
        int seqCount = ByteArrayUtils.decodeInt(packet, 12);
        tmPacket.setSequenceCount(seqCount);

        return tmPacket;
    }
}
