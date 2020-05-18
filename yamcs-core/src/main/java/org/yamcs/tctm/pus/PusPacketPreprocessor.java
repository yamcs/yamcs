package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.tctm.pus.Constants.*;

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
 * The time packets have no secondary header and the apid set to 0. The data part consists of the current onboard time in
 * the same encoding like in the normal packets.
 * 
 * <p>
 * In this class we are interested in the time and the sequence count.
 *
 * @author nm
 *
 */
public class PusPacketPreprocessor extends AbstractPacketPreprocessor {
    // where to look for time in the telemetry
    int pktTimeOffset;
    //the offset of the time inside the PUS time packets
    int timePktTimeOffset;
    
    public PusPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public PusPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        pktTimeOffset = config.getInt("pktTimeOffset", DEFAULT_PKT_TIME_OFFSET);
        timePktTimeOffset = config.getInt("timePktTimeOffset", DEFAULT_TIME_PACKET_TIME_OFFSET);
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();
        if (packet.length < 6) {
            eventProducer.sendWarning(
                    "Short packet received, length: " + packet.length + "; minimum required length is 6 bytes.");
            return null;
        }
        boolean secondaryHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(packet);

        if (!secondaryHeaderFlag) {// in PUS only time packets are allowed without secondary header and they should have
                                   // apid = 0
            int apid = CcsdsPacket.getAPID(packet);
            if (apid == 0) {
                processTimePacket(tmPacket);
                return tmPacket;
            }
            eventProducer.sendWarning("Packet with apid=" + apid + " and without secondary header received, ignoring.");
            return null;
        }

        if (packet.length < 12) {
            eventProducer.sendWarning(
                    "Short packet received, length: " + packet.length + "; minimum required length is 14 bytes.");
            return null;
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);

        boolean corrupted = false;

        if (errorDetectionCalculator != null) {
            int n = packet.length;
            int computedCheckword;
            try {
                computedCheckword = errorDetectionCalculator.compute(packet, 0, n - 2);
                int packetCheckword = ByteArrayUtils.decodeShort(packet, n - 2);
                if (packetCheckword != computedCheckword) {
                    eventProducer.sendWarning("Corrupted packet received, computed checkword: " + computedCheckword
                            + "; packet checkword: " + packetCheckword);
                    corrupted = true;
                }
            } catch (IllegalArgumentException e) {
                eventProducer.sendWarning("Error when computing checkword: " + e.getMessage());
                corrupted = true;
            }
        }

        long rectime = timeService.getMissionTime();
        long gentime;

        if (useLocalGenerationTime) {
            tmPacket.setLocalGenTime();
            gentime = rectime;
        } else {
            try {
                long t = timeDecoder.decode(packet, pktTimeOffset);
                gentime = timeEpoch == null ? t : shiftFromEpoch(timeEpoch, t);
            } catch (Exception e) {
                eventProducer.sendWarning("Failed to extract time from packet: " + e.getMessage());
                corrupted = true;
                gentime = rectime;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Recevied packet length: {}, apid: {}, seqcount: {}, gentime: {}, corrupted: {}", packet.length,
                    CcsdsPacket.getAPID(packet), CcsdsPacket.getSequenceCount(packet), TimeEncoding.toString(gentime),
                    corrupted);
        }

        tmPacket.setSequenceCount(apidseqcount);
        tmPacket.setGenerationTime(gentime);
        tmPacket.setInvalid(corrupted);
        return tmPacket;
    }

    private void processTimePacket(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket();
        long rectime = tmPacket.getReceptionTime();
        boolean corrupted = false;
        long gentime;
        if (useLocalGenerationTime) {
            tmPacket.setLocalGenTime();
            gentime = rectime;
        } else {
            try {
                long t = timeDecoder.decode(packet, timePktTimeOffset);
                gentime = timeEpoch == null ? t : shiftFromEpoch(timeEpoch, t);
            } catch (Exception e) {
                eventProducer.sendWarning("Failed to extract time from packet: " + e.getMessage());
                corrupted = true;
                gentime = rectime;
            }
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        tmPacket.setInvalid(corrupted);
        tmPacket.setSequenceCount(apidseqcount);
        tmPacket.setGenerationTime(gentime);
    }
}
