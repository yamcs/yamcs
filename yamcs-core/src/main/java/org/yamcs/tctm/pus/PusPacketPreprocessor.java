package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.ccsds.time.CcsdsTimeDecoder;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.tctm.pus.Constants.*;

/**
 * Implementation for ECSS PUS (ECSS-E-ST-70-41C) packets.
 * 
 * The header structure is:
 *
 * <p>Primary header (specified by CCSDS 133.0-B-1)
 * <ul>
 * <li>packet version number (3 bits)</li>
 * <li>packet type (1 bit)</li>
 * <li>secondary header flag (1 bit)</li>
 * <li>application process ID (11 bits)</li>
 * <li>sequence flags (2 bits)</li>
 * <li>packet sequence count (14 bits)</li>
 *</ul>
 *
 *<p>Secondary header (PUS specific)
 *<ul> 
 * <li>TM packet PUS version number (4 bits)</li>
 * <li>spacecraft time reference status (4 bits)</li>
 * <li>service type ID (8 bits)</li>
 * <li>message subtype ID (8 bits)</li>
 * <li>message type counter (16 bits)</li>
 * <li>destination ID (16 bits)</li>
 * <li>time (absolute time) variable</li>
 * <li>spare optional</li>
 * </ul>
 * 
 * <p>In this class we are interested in the time and the sequence count.
 *
 * @author nm
 *
 */
public class PusPacketPreprocessor extends AbstractPacketPreprocessor {
    final static Logger log = LoggerFactory.getLogger(PusPacketPreprocessor.class);

    //where to look for time in the telemetry
    int pktTimeOffset;

    CcsdsTimeDecoder timeDecoder = null;

    // time code ids as per CCSDS 301.0-B-4
    final static int PFIELD_TCID_TAI = 1;// 001 1-Jan-1958 epoch
    final static int PFIELD_TCID_AGENCY_EPOCH = 2; // 010 agency defined epoch
    final static int PFIELD_TCID_CDS = 4; // 100 CCSDS DAY SEGMENTED TIME CODE
    final static int PFIELD_TCID_CCS = 5; // 101 CCSDS CALENDAR SEGMENTED TIME CODE
    final static int PFIELD_TCID_LEVEL34 = 6; // 110 Level 3 or 4 Agency-defined code (i.e. not defined in the standard)

    public PusPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public PusPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        super(yamcsInstance, config);
        configureTimeDecoder(config);
    }


    void configureTimeDecoder(Map<String, Object> config) {
        if (config != null && config.containsKey(Constants.CONFIG_KEY_TIME_ENCODING)) {
            Map<String, Object> c = YConfiguration.getMap(config, Constants.CONFIG_KEY_TIME_ENCODING);
            String type = YConfiguration.getString(c, "type", "CUC");
            if ("CUC".equals(type)) {
                int implicitPField = YConfiguration.getInt(config, "implicitPField", DEFAULT_IMPLICIT_PFIELD);
                timeDecoder = new CucTimeDecoder(implicitPField);
            } else {
                throw new ConfigurationException("Time encoding of type '" + type + " not supported. Supported: CUC");
            }
            pktTimeOffset = YConfiguration.getInt(c, "pktTimeOffset", DEFAULT_PKT_TIME_OFFSET);
        } else {
            pktTimeOffset = DEFAULT_PKT_TIME_OFFSET;
            timeDecoder = new CucTimeDecoder(DEFAULT_IMPLICIT_PFIELD);
        }
        log.debug("Using time decoder {}", timeDecoder);
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        boolean secondaryHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(packet);

        if (!secondaryHeaderFlag) {// TODO PUS does actually allow the time packets without the secondary header
            eventProducer.sendWarning("Packet without secondary header received, ignoring.");
            return null;
        }

        if (packet.length < 12) {
            eventProducer.sendWarning(
                    "Short packet received, length: " + packet.length + "; minimum required length is 14 bytes.");
            return null;
        }
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        boolean checksumIndicator = CcsdsPacket.getChecksumIndicator(packet);

        boolean corrupted = false;

        if (checksumIndicator) {
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
        try {
            gentime = timeDecoder.decode(packet, pktTimeOffset);
        } catch (Exception e) {
            eventProducer.sendWarning("Failed to extract time from packet: " + e.getMessage());
            corrupted = true;
            gentime = rectime;
        }
        
        if (log.isTraceEnabled()) {
            log.trace("Recevied packet length: {}, apid: {}, seqcount: {}, gentime: {}, corrupted: {}", packet.length,
                    CcsdsPacket.getAPID(packet), CcsdsPacket.getSequenceCount(packet), TimeEncoding.toString(gentime),
                    corrupted);
        }

        PacketWithTime pwt = new PacketWithTime(gentime, CcsdsPacket.getInstant(packet), apidseqcount, packet);
        pwt.setCorrupted(corrupted);
        return pwt;
    }
}
