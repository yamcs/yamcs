package org.yamcs.tctm.cfs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Preprocessor for the CFS TM packets:
 * <ul>
 * <li>CCSDS primary header 6 bytes</li>
 * <li>Time seconds (GPS -TBD) 4 bytes</li>
 * <li>Time milliseconds 2 bytes</li>
 * </ul>
 */
public class CfsPacketPreprocessor extends AbstractPacketPreprocessor {
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<>();
    private final Log log;
    static final int MINIMUM_LENGTH = 12;
    private boolean checkForSequenceDiscontinuity = true;

    public CfsPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public CfsPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        this.log = new Log(getClass(), yamcsInstance);
    }

    @Override
    public TmPacket process(TmPacket pwt) {
        byte[] packet = pwt.getPacket();
        if (packet.length < MINIMUM_LENGTH) {
            eventProducer.sendWarning("SHORT_PACKET",
                    "Short packet received, length: " + packet.length + "; minimum required length is " + MINIMUM_LENGTH
                            + " bytes.");
            return null;
        }
        int apidseqcount = ByteArrayUtils.decodeInt(packet, 0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;
        AtomicInteger ai = seqCounts.computeIfAbsent(apid, k -> new AtomicInteger());
        int oldseq = ai.getAndSet(seq);

        

        if (checkForSequenceDiscontinuity && ((seq - oldseq) & 0x3FFF) != 1) {
            eventProducer.sendWarning("SEQ_COUNT_JUMP",
                    "Sequence count jump for apid: " + apid + " old seq: " + oldseq + " newseq: " + seq);
        }
        if(useLocalGenerationTime) {
            pwt.setLocalGenTime();
            pwt.setGenerationTime(timeService.getMissionTime());
        } else {
            pwt.setGenerationTime(getTimeFromPacket(packet));
        }
        pwt.setSequenceCount(apidseqcount);
        if (log.isTraceEnabled()) {
            log.trace("processing packet apid: {}, seqCount:{}, length: {}, genTime: {}", apid, seq, packet.length, TimeEncoding.toString(pwt.getGenerationTime()));
        }
        return pwt;
    }

    static long getTimeFromPacket(byte[] packet) {
        long sec = ByteArrayUtils.decodeIntLE(packet, 6) & 0xFFFFFFFFL;
        int subsecs = ByteArrayUtils.decodeShortLE(packet, 10);

        return TimeEncoding.fromGpsMillisec(1000 * sec + subsecs*1000/65536);
    }

    public boolean checkForSequenceDiscontinuity() {
        return checkForSequenceDiscontinuity;
    }

    @Override
    public void checkForSequenceDiscontinuity(boolean checkForSequenceDiscontinuity) {
        this.checkForSequenceDiscontinuity = checkForSequenceDiscontinuity;
    }

}
