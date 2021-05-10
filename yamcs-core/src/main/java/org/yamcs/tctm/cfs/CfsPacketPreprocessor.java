package org.yamcs.tctm.cfs;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Preprocessor for the CFS TM packets:
 * <ul>
 * <li>CCSDS primary header 6 bytes</li>
 * <li>Time seconds 4 bytes</li>
 * <li>subseconds(1/2^16 fraction of seconds) 2 bytes</li>
 * </ul>
 * 
 * Options:
 * 
 * <pre>
 *   dataLinks:
 *   ...
 *      packetPreprocessor: org.yamcs.tctm.cfs.CfsPacketPreprocessor
 *      packetPreprocessorArgs:
 *          byteOrder: LITTLE_ENDIAN
 *          timeEncoding:
 *              epoch: CUSTOM
 *              epochUTC: 1970-01-01T00:00:00Z
 *              timeIncludesLeapSeconds: false
 * 
 * </pre>
 * 
 * The {@code byteOrder} option (default is {@code BIG_ENDIAN}) is used only for decoding the timestamp in the secondary
 * header: the 4 bytes second and 2 bytes
 * subseconds are decoded in little endian.
 * <p>
 * The primary CCSDS header is always decoded as BIG_ENDIAN.
 * <p>
 * For explanation on the {@code timeEncoding} property, please see {@link AbstractPacketPreprocessor}. The default
 * timeEncoding used if none is specified, is GPS, equivalent with this configuration:
 * 
 * <pre>
 * timeEncoding:
 *     epoch: GPS
 * </pre>
 * 
 * which is also equivalent with this more detailed configuration:
 * 
 * <pre>
 * timeEncoding:
 *     epoch: CUSTOM
 *     epochUTC: "1980-01-06T00:00:00Z"
 *     timeIncludesLeapSeconds: true
 * </pre>
 */
public class CfsPacketPreprocessor extends AbstractPacketPreprocessor {
    protected enum CfeTimeStampFormat {
        CFE_SB_TIME_32_16_SUBS,
        CFE_SB_TIME_32_32_SUBS,
        CFE_SB_TIME_32_32_M_20
    }
	
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<>();
    static final int MINIMUM_LENGTH = 12;
    private boolean checkForSequenceDiscontinuity = true;
    protected static CfeTimeStampFormat timestampFormat = CfeTimeStampFormat.CFE_SB_TIME_32_16_SUBS;
    protected int timestampLength = 6;

    public CfsPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public CfsPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        
        this.byteOrder = AbstractPacketPreprocessor.getByteOrder(config);
        
        if (!config.containsKey(CONFIG_KEY_TIME_ENCODING)) {
            this.timeEpoch = TimeEpochs.GPS;
        }

        String format = config.getString("timestampFormat");
        
        if(format.equals("CFE_SB_TIME_32_16_SUBS")) {
            this.timestampFormat = CfeTimeStampFormat.CFE_SB_TIME_32_16_SUBS;
            this.timestampLength = 6;
        } else if(format.equals("CFE_SB_TIME_32_32_SUBS")) {
            this.timestampFormat = CfeTimeStampFormat.CFE_SB_TIME_32_32_SUBS;
            this.timestampLength = 8;
        } else if(format.equals("CFE_SB_TIME_32_32_M_20")) {
            this.timestampFormat = CfeTimeStampFormat.CFE_SB_TIME_32_32_M_20;
            this.timestampLength = 8;
        } else {
        	throw new ConfigurationException("Invalid timestampFormat (CFE_SB_TIME_32_16_SUBS, CFE_SB_TIME_32_32_SUBS, or CFE_SB_TIME_32_32_M_20)");
        }        

        this.checkForSequenceDiscontinuity = config.getBoolean("checkForSequenceDiscontinuity", true);
    }

    @Override
    public TmPacket process(TmPacket pkt) {
        byte[] packet = pkt.getPacket();
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
        pkt.setSequenceCount(apidseqcount);
        if (useLocalGenerationTime) {
            pkt.setLocalGenTimeFlag();
            pkt.setGenerationTime(timeService.getMissionTime());
        } else {
            pkt.setGenerationTime(getTimeFromPacket(packet));
            if (tcoService != null) {
                tcoService.verify(pkt);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("processing packet apid: {}, seqCount:{}, length: {}, genTime: {}", apid, seq, packet.length,
                    TimeEncoding.toString(pkt.getGenerationTime()));
        }
        return pkt;
    }

    long getTimeFromPacket(byte[] packet) {
        long sec = 0;
        long subsecs = 0;
        long maxSubSecs = 0;

        if (byteOrder == ByteOrder.BIG_ENDIAN) {       	
            sec = ByteArrayUtils.decodeInt(packet, 6) & 0xFFFFFFFFL;

            switch(this.timestampFormat) {
                case CFE_SB_TIME_32_16_SUBS: {
                    subsecs = ByteArrayUtils.decodeUnsignedShort(packet, 10);
                    maxSubSecs = 65536L;
                    break;
                }
                    
                case CFE_SB_TIME_32_32_SUBS: {
                    subsecs = ByteArrayUtils.decodeUnsignedInt(packet, 10);
                    maxSubSecs = 4294967296L;
                    break;
                }
                    
                case CFE_SB_TIME_32_32_M_20: {
                    subsecs = ByteArrayUtils.decodeUnsignedInt(packet, 10);
                    maxSubSecs = 4294967296L;
                    break;
                }
            }
        } else {
            sec = ByteArrayUtils.decodeIntLE(packet, 6) & 0xFFFFFFFFL;
            
            switch(this.timestampFormat) {
                case CFE_SB_TIME_32_16_SUBS: {
                    subsecs = ByteArrayUtils.decodeUnsignedShortLE(packet, 10);
                    maxSubSecs = 65536L;
                    break;
                }
                    
                case CFE_SB_TIME_32_32_SUBS: {
                    subsecs = ByteArrayUtils.decodeUnsignedIntLE(packet, 10);
                    maxSubSecs = 4294967296L;
                    break;
                }
                    
                case CFE_SB_TIME_32_32_M_20: {
                    subsecs = ByteArrayUtils.decodeUnsignedIntLE(packet, 10);
                    maxSubSecs = 4294967296L;
                    break;
                }
            }
        }

        return shiftFromEpoch((1000 * sec) + ((subsecs * 1000) / maxSubSecs));
    }

    public boolean checkForSequenceDiscontinuity() {
        return checkForSequenceDiscontinuity;
    }

    @Override
    public void checkForSequenceDiscontinuity(boolean checkForSequenceDiscontinuity) {
        this.checkForSequenceDiscontinuity = checkForSequenceDiscontinuity;
    }

}
