package org.yamcs.tctm.cfs;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacketPreprocessor;
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
public class CfsPacketPreprocessor extends CcsdsPacketPreprocessor {
    static final int MINIMUM_LENGTH = 12;

    public CfsPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public CfsPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        if (!config.containsKey(CONFIG_KEY_TIME_ENCODING)) {
            this.timeEpoch = TimeEpochs.GPS;
        }
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

        checkSequence(apid, seq);

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
        long sec;
        int subsecs;

        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            sec = ByteArrayUtils.decodeInt(packet, 6) & 0xFFFFFFFFL;
            subsecs = ByteArrayUtils.decodeUnsignedShort(packet, 10);
        } else {
            sec = ByteArrayUtils.decodeIntLE(packet, 6) & 0xFFFFFFFFL;
            subsecs = ByteArrayUtils.decodeUnsignedShortLE(packet, 10);
        }
        return shiftFromEpoch(1000 * sec + subsecs * 1000 / 65536);
    }
}
