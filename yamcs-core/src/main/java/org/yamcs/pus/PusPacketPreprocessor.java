package org.yamcs.pus;

import static org.yamcs.pus.Constants.*;

import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsPacketPreprocessor;
import org.yamcs.tctm.ccsds.FrameStreamHelper;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Implementation for ECSS PUS (ECSS-E-ST-70-41C) packets.
 *
 * The header structure is:
 *
 * <p>
 * Primary header (specified by CCSDS 133.0-B-1)
 * <ul>
 * <li>packet version number (3 bits)</li>
 * <li>packet type (1 bit)</li>
 * <li>secondary header flag (1 bit)</li>
 * <li>application process ID (11 bits)</li>
 * <li>sequence flags (2 bits)</li>
 * <li>packet sequence count (14 bits)</li>
 * </ul>
 *
 * <p>
 * Secondary header (PUS specific)
 * <ul>
 * <li>TM packet PUS version number (4 bits)</li>
 * <li>spacecraft time reference status (4 bits)</li>
 * <li>service type ID (8 bits)</li>
 * <li>message subtype ID (8 bits)</li>
 * <li>message type counter (16 bits)</li>
 * <li>destination ID (16 bits)</li>
 * <li>time (absolute time) variable</li>
 * <li>spare optional</li>
 * </ul>
 * <p>
 * The time packets have no secondary header and the apid set to 0. The data part consists of the current onboard time
 * in the same encoding like in the normal packets.
 *
 * <p>
 * In this class we read
 * <ul>
 * <li>the APID and sequence count from the CCSDS primary header interested in the time and the sequence count.</li>
 * <li>the time from the PUS secondary header and from the time packets.</li>
 * </ul>
 * <p>
 * The offset of where the time is read from is configurable as this has changed between different versions of the
 * standards.
 * <p>
 * Example configuration:
 * 
 * <pre>
 * dataLinks:
 * ...
 * - name: tm_realtime
 *   packetPreprocessorClassName: org.yamcs.tctm.pus.PusPacketPreprocessor
 *   packetPreprocessorArgs:
 *     errorDetection:
 *       type: CRC-16-CCIIT
 *     pktTimeOffset: 13
 *     timePktTimeOffset: 7
 *     timeEncoding:
 *       epoch: CUSTOM
 *       epochUTC: 1970-01-01T00:00:00Z
 *       timeIncludesLeapSeconds: false
 * </pre>
 *
 * Another example with time correlation:
 *
 * <pre>
 * dataLinks:
 * ...
 * - name: tm_realtime
 *   packetPreprocessorClassName: org.yamcs.tctm.pus.PusPacketPreprocessor
 *   packetPreprocessorArgs:
 *     errorDetection:
 *       type: CRC-16-CCIIT
 *     pktTimeOffset: 13
 *     timePktTimeOffset: 7
 *     performTimeCorrelation: true
 *     goodFrameStream: good_frames
 *     tcoService: tco0
 * </pre>
 *
 * In this second example, the goodFramesStream option is required because the preprocessor will monitor the stream to
 * determine the earth reception time of the frame to which the time packet refers to.
 */
public class PusPacketPreprocessor extends CcsdsPacketPreprocessor {
    // where to look for time in the telemetry
    int pktTimeOffset;
    // the offset of the time inside the PUS time packets
    int timePktTimeOffset;

    boolean performTimeCorrelation;
    RateErtRecorder ertRecorder;

    public PusPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public PusPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        pktTimeOffset = config.getInt("pktTimeOffset", DEFAULT_PKT_TIME_OFFSET);
        performTimeCorrelation = config.getBoolean("performTimeCorrelation", false);

        if (timeDecoder == null) {
            this.timeDecoder = new CucTimeDecoder(-1);
            this.timeEpoch = TimeEpochs.GPS;
        }
        if (performTimeCorrelation) {
            ertRecorder = new RateErtRecorder();
            timePktTimeOffset = config.getInt("timePktTimeOffset", DEFAULT_TIME_PACKET_TIME_OFFSET);

            String goodFrameStream = config.getString("goodFrameStream", "good_frames");
            var ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream stream = ydb.getStream(goodFrameStream);
            if (stream == null) {
                throw new ConfigurationException("Cannot find stream " + goodFrameStream);
            }
            stream.addSubscriber(this::processFrameTuple);
        }
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();
        if (packet.length < 6) {
            eventProducer.sendWarning("Short packet received, length: " + packet.length
                    + "; minimum required length is 6 bytes.");
            return null;
        }
        verifyCrc(tmPacket);
        if (tmPacket.isInvalid()) {
            // if the CRC has failed, do not go further
            return null;
        }
        int apidseqcount = ByteArrayUtils.decodeInt(packet, 0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;
        checkSequence(apid, seq);

        boolean secondaryHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(packet);

        if (!secondaryHeaderFlag) {
            // in PUS only time packets are allowed without secondary header and they should have apid = 0
            if (apid == 0) {
                processTimePacket(tmPacket);
                return tmPacket;
            }
            eventProducer.sendWarning("Packet with apid=" + apid
                    + " and without secondary header received, ignoring.");
            return null;
        }

        if (packet.length < 12) {
            eventProducer.sendWarning("Short packet received, length: " + packet.length
                    + "; minimum required length is 14 bytes.");
            return null;
        }

        tmPacket.setSequenceCount(apidseqcount);

        setRealtimePacketTime(tmPacket, pktTimeOffset);

        if (log.isTraceEnabled()) {
            log.trace("Received packet length: {}, apid: {}, seqcount: {}, gentime: {}, status: {}", packet.length,
                    CcsdsPacket.getAPID(packet), CcsdsPacket.getSequenceCount(packet),
                    TimeEncoding.toString(tmPacket.getGenerationTime()),
                    Integer.toHexString(tmPacket.getStatus()));
        }

        return tmPacket;
    }

    private void processFrameTuple(Stream stream, Tuple tuple) {
        long vcid = tuple.getIntColumn(FrameStreamHelper.VCID_CNAME);
        if (vcid != 0) {
            return;
        }
        long seq = tuple.getLongColumn(FrameStreamHelper.FRAME_SEQ_CNAME);
        Instant ert = tuple.getColumn(FrameStreamHelper.ERTIME_CNAME);
        ertRecorder.update(seq, ert);
    }

    private void processTimePacket(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();
        boolean corrupted = false;

        int rate = packet[6] & 0xFF;
        if (performTimeCorrelation) {
            long obt = timeDecoder.decodeRaw(packet, timePktTimeOffset);
            Instant ert = ertRecorder.get(rate, tmPacket.getFrameSeqCount());
            if (ert != null) {
                log.debug("Adding tco sample obt: {} , ert: {}", obt, ert);
                tcoService.addSample(obt, ert);
            }
        }

        setRealtimePacketTime(tmPacket, timePktTimeOffset);

        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        tmPacket.setInvalid(corrupted);
        tmPacket.setSequenceCount(apidseqcount);
    }

    static class RateErtRecorder {
        long frameSeqNum;
        Instant ert;
        int rate = 4;

        /**
         * if the last rate bits of frameSeqNumber are 0, then store the ert
         * <p>
         * if all the bits except the last rate bits are different, then clear the ert
         */
        public synchronized void update(long frameSeqNumber, Instant ert) {
            if ((frameSeqNumber & ((1L << rate) - 1)) == 0) {
                this.ert = ert;
                this.frameSeqNum = frameSeqNumber;
            } else if ((this.frameSeqNum >> rate) != (frameSeqNumber >> rate)) {
                this.ert = null;
            }
        }

        /**
         * if all the bits except the last rate are the same then return the currently stored ert, otherwise return null
         */
        public synchronized Instant get(int rate, long frameSeqNumber) {
            this.rate = rate;

            if ((this.frameSeqNum >> rate) == (frameSeqNumber >> rate)) {
                return ert;
            } else {
                return null;
            }
        }
    }
}
