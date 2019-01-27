package org.yamcs.tctm;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.archive.PacketWithTime;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.CcsdsPacket;


/**
 * This implements CCSDS packets as used in ISS (International Space Station).
 * <br>
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
 *If the checksum indicator is 1, the packet is terminated by a two bytes checksum.
 *<br>
 * This class is effectively making use of the following fields (all the other ones are ignored): 
 * <ul>
 * <li>application process ID(APID) and packet sequence count: used to detect the discontinuity in packets as well as to form the unique key (used to not store duplicates)</li>
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
public class IssPacketPreprocessor extends AbstractPacketPreprocessor {
    ErrorDetectionWordCalculator errorDetectionCalculator;
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<Integer, AtomicInteger>();

    public IssPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public IssPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        super(yamcsInstance, config);

        if (errorDetectionCalculator==null) {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        if (packet.length < 16) {
            eventProducer.sendWarning("SHORT_PACKET",
                    "Short packet received, length: " + packet.length + "; minimum required length is 16 bytes.");
            return null;
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;
        AtomicInteger ai = seqCounts.computeIfAbsent(apid, k -> new AtomicInteger());
        int oldseq = ai.getAndSet(seq);
        if (((seq - oldseq) & 0x3FFF) != 1) {
            eventProducer.sendWarning("SEQ_COUNT_JUMP",
                    "Sequence count jump for apid: "+apid+" old seq: "+oldseq+" newseq: "+seq);
        }

        boolean checksumIndicator = CcsdsPacket.getChecksumIndicator(packet);
        boolean corrupted = false;

        if (checksumIndicator) {
            int n = packet.length;
            int computedCheckword;
            try {
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

        PacketWithTime pwt = new PacketWithTime(timeService.getMissionTime(), CcsdsPacket.getInstant(packet),
                apidseqcount, packet);
        pwt.setCorrupted(corrupted);
        return pwt;
    }
    
    
    

}
