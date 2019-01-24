package org.yamcs.tctm;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.ccsds.CrcCciitCalculator;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.CcsdsPacket;

import static org.yamcs.tctm.IssCommandPostprocessor.CONFIG_KEY_ERROR_DETECTION;

/**
 * This implements CCSDS packets as used in ISS (International Space Station)
 * 
 * @author nm
 *
 */
public class IssPacketPreprocessor implements PacketPreprocessor {
    TimeService timeService;
    EventProducer eventProducer;
    ErrorDetectionWordCalculator errorDetectionCalculator;
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<Integer, AtomicInteger>();

    public IssPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public IssPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        timeService = YamcsServer.getTimeService(yamcsInstance);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setRepeatedEventReduction(true, 10000);
        eventProducer.setSource(this.getClass().getSimpleName());
        if (config != null && config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            Map<String, Object> c = YConfiguration.getMap(config, CONFIG_KEY_ERROR_DETECTION);
            String type = YConfiguration.getString(c, "type");
            if ("16-SUM".equalsIgnoreCase(type)) {
                errorDetectionCalculator = new Running16BitChecksumCalculator();
            } else if ("CRC-16-CCIIT".equalsIgnoreCase(type)) {
                errorDetectionCalculator = new CrcCciitCalculator(c);
            } else {
                throw new ConfigurationException(
                        "Unknown errorDetectionWord type '" + type + "': supported types are 16-SUM and CRC-16-CCIIT");
            }
        } else {
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
                    eventProducer.sendWarning("CORRUPTED_PACKET",
                            "Corrupted packet received, computed checkword: " + computedCheckword
                                    + "; packet checkword: " + packetCheckword);
                    corrupted = true;
                }
            } catch (IllegalArgumentException e) {
                eventProducer.sendWarning("CORRUPTED_PACKET",
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
