package org.yamcs.tctm.cfs;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.IssPacketPreprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Preprocessor for the CFS TM packets
 * <li>CCSDS primary header 6 bytes</li>
 * <li>Time seconds (GPS -TBD) 4 bytes</li>
 * <li>Time milliseconds 2 bytes </li>
 *
 */
public class CfsPacketPreprocessor extends AbstractPacketPreprocessor {
    ErrorDetectionWordCalculator errorDetectionCalculator;
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<Integer, AtomicInteger>();
    private static final Logger log = LoggerFactory.getLogger(IssPacketPreprocessor.class);
    static final int MINIMUM_LENGTH = 12;
    
    public CfsPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public CfsPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        super(yamcsInstance, config);     
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        if (packet.length < MINIMUM_LENGTH) {
            eventProducer.sendWarning("SHORT_PACKET",
                    "Short packet received, length: " + packet.length + "; minimum required length is "+MINIMUM_LENGTH+" bytes.");
            return null;
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;
        AtomicInteger ai = seqCounts.computeIfAbsent(apid, k -> new AtomicInteger());
        int oldseq = ai.getAndSet(seq);
        
        if(log.isTraceEnabled()) {
            log.trace("processing packet apid: {}, seqCount:{}, length: {}", apid, seq, packet.length);
        }
        
        
        if (((seq - oldseq) & 0x3FFF) != 1) {
            eventProducer.sendWarning("SEQ_COUNT_JUMP",
                    "Sequence count jump for apid: "+apid+" old seq: "+oldseq+" newseq: "+seq);
        }

      
        PacketWithTime pwt = new PacketWithTime(timeService.getMissionTime(), getTime(packet),
                apidseqcount, packet);
        return pwt;
    }
    
    
    static long getTime(byte[] packet) {
        long sec = ByteArrayUtils.decodeInt(packet, 6)&0xFFFFFFFFL;
        int millisec =  ByteArrayUtils.decodeShort(packet, 6+10);
        
        return  TimeEncoding.fromGpsMillisec(sec*1000+millisec);
    }
    


}
