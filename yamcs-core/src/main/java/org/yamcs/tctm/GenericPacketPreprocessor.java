package org.yamcs.tctm;

import java.nio.ByteOrder;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.time.FixedSizeTimeDecoder;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SequenceContainer;

/**
 * Generic packet preprocessor.
 * <p>
 * Reads the timestamp (8 bytes) and the sequence count (4 bytes) from a user defined offset.
 * <p>
 * Optionally allows to specify also a checksum algorithm to be used. The checksum is at the end of the packet.
 * 
 * <table>
 * <tr>
 * <td>timestampOffset</td>
 * <td>Offset in the packet where to read the 8 bytes timestamp from. If negative, do not read the timestmap from within
 * the packet but use the local wallclock time instead. The way to translate the timestamp to Yamcs time is configured
 * by the {@code timeEncoding} property.</td>
 * </tr>
 * <tr>
 * <td>seqCountOffset</td>
 * <td>Offset in the packet where to read the sequence count from. If negative, do not read the sequence count from
 * within the packet and set it to 0 instead.
 * <p>
 * Note: this class does not check for sequence count continuity.</td>
 * </tr>
 * <tr>
 * <td>errorDetection</td>
 * <td>If present, specify which error detection to use. Example: errorDetection: <br>
 * &nbsp;&nbsp;-type: "CRC-16-CCIIT"</td>
 * </tr>
 * <tr>
 * <td>byteOrder</td>
 * <td>Can be BIG_ENDIAN (default) or LITTLE_ENDIAN. Configures the byte order used for reading the timestamp, sequence
 * count and crc</td>
 * </tr>
 * <tr>
 * <td>timeEncoding</td>
 * <td>Can be used to configure the way the timestamp is translated to Yamcs time. See the
 * {@link AbstractPacketPreprocessor} for details. If this option is not specified, the default epoch used is UNIX.
 * </table>
 */
public class GenericPacketPreprocessor extends AbstractPacketPreprocessor {

    // where from the packet to read the 8 bytes timestamp
    final int timestampOffset;

    // where from the packet to read the 4 bytes sequence count
    final int seqCountOffset;

    // Optional. If unset Yamcs will attempt to determine it in other ways
    SequenceContainer rootContainer;

    public GenericPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        timestampOffset = config.getInt("timestampOffset");
        seqCountOffset = config.getInt("seqCountOffset");

        if (timeDecoder == null) {
            this.timeDecoder = new FixedSizeTimeDecoder(byteOrder, 8, 1);
            this.timeEpoch = TimeEpochs.UNIX;
        }

        var rootContainerName = config.getString("rootContainer", null);
        if (rootContainerName != null) {
            var mdb = MdbFactory.getInstance(yamcsInstance);
            rootContainer = mdb.getSequenceContainer(rootContainerName);
            if (rootContainer == null) {
                throw new ConfigurationException(
                        "MDB does not have a sequence container named '" + rootContainerName + "'");
            }
        }
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
                int packetCheckword = (byteOrder == ByteOrder.BIG_ENDIAN)
                        ? ByteArrayUtils.decodeUnsignedShort(packet, n - 2)
                        : ByteArrayUtils.decodeUnsignedShortLE(packet, n - 2);

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
        if (timestampOffset < 0) {
            tmPacket.setGenerationTime(TimeEncoding.getWallclockTime());
        } else {
            setRealtimePacketTime(tmPacket, timestampOffset);
        }

        int seqCount = 0;
        if (seqCountOffset >= 0) {
            if (packet.length < seqCountOffset + 4) {
                eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, "Packet too short to extract sequence count");
                seqCount = -1;
                corrupted = true;
            } else {
                seqCount = (byteOrder == ByteOrder.BIG_ENDIAN)
                        ? ByteArrayUtils.decodeInt(packet, seqCountOffset)
                        : ByteArrayUtils.decodeIntLE(packet, seqCountOffset);
            }
        }

        tmPacket.setSequenceCount(seqCount);
        tmPacket.setInvalid(corrupted);
        tmPacket.setRootContainer(rootContainer);
        return tmPacket;
    }

    @Override
    protected TimeDecoderType getDefaultDecoderType() {
        return TimeDecoderType.FIXED;
    }
}
