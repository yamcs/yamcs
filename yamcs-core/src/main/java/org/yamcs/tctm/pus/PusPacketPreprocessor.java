package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.ccsds.time.CcsdsTimeDecoder;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
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
 * The time packets have no secondary header and the apid set to 0. The data part consist of the current onboard time in
 * the same encoding like in the normal packets.
 * 
 * <p>
 * In this class we are interested in the time and the sequence count.
 *
 * @author nm
 *
 */
public class PusPacketPreprocessor extends AbstractPacketPreprocessor {
    final static Logger log = LoggerFactory.getLogger(PusPacketPreprocessor.class);

    // where to look for time in the telemetry
    int pktTimeOffset;

    CcsdsTimeDecoder timeDecoder = null;

    // if true, do not extract time from packets
    boolean useLocalGenerationTime;

    // time code ids as per CCSDS 301.0-B-4
    final static int PFIELD_TCID_TAI = 1;// 001 1-Jan-1958 epoch
    final static int PFIELD_TCID_AGENCY_EPOCH = 2; // 010 agency defined epoch
    final static int PFIELD_TCID_CDS = 4; // 100 CCSDS DAY SEGMENTED TIME CODE
    final static int PFIELD_TCID_CCS = 5; // 101 CCSDS CALENDAR SEGMENTED TIME CODE
    final static int PFIELD_TCID_LEVEL34 = 6; // 110 Level 3 or 4 Agency-defined code (i.e. not defined in the standard)

    public PusPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public PusPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        configureTimeDecoder(config);
    }

    void configureTimeDecoder(YConfiguration config) {
        if (config != null) {
            useLocalGenerationTime = config.getBoolean("useLocalGenerationTime", false);
        }

        if (!useLocalGenerationTime && config != null && config.containsKey(Constants.CONFIG_KEY_TIME_ENCODING)) {
            YConfiguration c = config.getConfig(Constants.CONFIG_KEY_TIME_ENCODING);
            String type = c.getString("type", "CUC");
            if ("CUC".equals(type)) {
                int implicitPField = c.getInt("implicitPField", DEFAULT_IMPLICIT_PFIELD);
                timeDecoder = new CucTimeDecoder(implicitPField);
            } else {
                throw new ConfigurationException("Time encoding of type '" + type
                        + " not supported. Supported: CUC=CCSDS unsegmented time");
            }
            pktTimeOffset = c.getInt("pktTimeOffset", DEFAULT_PKT_TIME_OFFSET);
        } else {
            pktTimeOffset = DEFAULT_PKT_TIME_OFFSET;
            timeDecoder = new CucTimeDecoder(DEFAULT_IMPLICIT_PFIELD);
        }
        log.debug("Using time decoder {}", timeDecoder);
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
            gentime = rectime;
        } else {
            try {
                gentime = timeDecoder.decode(packet, pktTimeOffset);
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
        tmPacket.setCorrupted(corrupted);
        return tmPacket;
    }

    private void processTimePacket(TmPacket tmPacket) {
        byte[] packet = tmPacket.getPacket(); 
        long rectime = tmPacket.getReceptionTime();
        boolean corrupted = false;
        long gentime;
        if (useLocalGenerationTime) {
            gentime = rectime;
        } else {
            try {
                gentime = timeDecoder.decode(packet, 6);
            } catch (Exception e) {
                eventProducer.sendWarning("Failed to extract time from packet: " + e.getMessage());
                corrupted = true;
                gentime = rectime;
            }
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        tmPacket.setCorrupted(corrupted);
        tmPacket.setSequenceCount(apidseqcount);
        tmPacket.setGenerationTime(gentime);
    }
}
