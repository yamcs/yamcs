package org.yamcs.tctm;

import java.nio.ByteBuffer;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * This implements CCSDS packets as used in ISS (International Space Station). <br>
 * Primary header (specified by CCSDS 133.0-B-1):
 * <ul>
 * <li>packet version number (3 bits)</li>
 * <li>packet type (1 bit)</li>
 * <li>secondary header flag (1 bit)</li>
 * <li>application process ID (11 bits)</li>
 * <li>sequence flags (2 bits)</li>
 * <li>packet sequence count (14 bits)</li>
 * </ul>
 * <br>
 * Secondary header (specific to ISS):
 * <ul>
 * <li>coarse time (32 bits)</li>
 * <li>fine time (8 bits)</li>
 * <li>time id(2 bits)</li>
 * <li>checksum indicator (1 bit)</li>
 * <li>spare (1 bit)</li>
 * <li>packet type (4 bits)</li>
 * <li>packet ID(32 bits)</li>
 * </ul>
 *
 * If the checksum indicator is 1, the packet is terminated by a two bytes checksum. <br>
 * This class is effectively making use of the following fields (all the other ones are ignored):
 * <ul>
 * <li>application process ID(APID) and packet sequence count: used to detect the discontinuity in packets as well as to
 * form the unique key (used to not store duplicates)</li>
 * <li>coarse time and fine time: used to derive the timestamp of the packet</li>
 * <li>checksum indicator: used to know to compute and verify or not the checksum</li>
 * </ul>
 * The checksum used can be one of
 * <ul>
 * <li>16-SUM (default): running sum on each two bytes - the packet has to contain an even number of bytes</li>
 * <li>CRC-16-CCIIT: CRC with the generator polynomial x^16 + x^12 + x^5 + 1</li>
 * </ul>
 *
 * @author nm
 *
 */
public class IssPacketPreprocessor extends CcsdsPacketPreprocessor {

    public IssPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public IssPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);

        if (errorDetectionCalculator == null) {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();

        if (packet.length < 16) {
            eventProducer.sendWarning("SHORT_PACKET",
                    "Short packet received, length: " + packet.length + "; minimum required length is 16 bytes.");
            return null;
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;

        if (log.isTraceEnabled()) {
            log.trace("processing packet apid: {}, seqCount:{}, length: {}", apid, seq, packet.length);
        }

        boolean checksumIndicator = CcsdsPacket.getChecksumIndicator(packet);
        boolean corrupted = false;

        if (checksumIndicator) {
            int n = packet.length;
            int computedCheckword;
            try {
                computedCheckword = errorDetectionCalculator.compute(packet, 0, n - 2);
                int packetCheckword = ByteArrayUtils.decodeUnsignedShort(packet, n - 2);
                if (packetCheckword != computedCheckword) {
                    String message = "Corrupted packet received, computed checkword: " + computedCheckword
                            + "; packet checkword: " + packetCheckword;
                    log.warn(message);
                    eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET, message);
                    corrupted = true;
                }
            } catch (IllegalArgumentException e) {
                eventProducer.sendWarning(ETYPE_CORRUPTED_PACKET,
                        "Error when computing checkword: " + e.getMessage());
                corrupted = true;
            }
        }

        checkSequence(apid, seq);

        long genTime = TimeEncoding.fromGpsCcsdsTime(ByteArrayUtils.decodeInt(packet, 6), packet[10]);
        tmPacket.setGenerationTime(genTime);
        tmPacket.setSequenceCount(apidseqcount);
        tmPacket.setInvalid(corrupted);
        return tmPacket;
    }

}
