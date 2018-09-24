package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.CrcCciitCalculator;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.tctm.ccsdstime.CcsdsTimeDecoder;
import org.yamcs.tctm.ccsdstime.CucTimeDecoder;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.tctm.pus.Constants.*;

/**
 * Implementation for ECSS PUS (ECSS-E-ST-70-41C) packets.
 * 
 * The header structure is:
 *
 * <pre>
 * Primary header (specified by CCSDS 133.0-B-1)
 * 
 * packet version number (3 bits)
 * packet type (1 bit)
 * secondary header flag (1 bit)
 * application process ID (11 bits)
 * sequence flags (2 bits)
 * packet sequence count (14 bits)
 *
 * Secondary header (PUS specific)
 * 
 * TM packet PUS version number (4 bits)
 * spacecraft time reference status (4 bits)
 * service type ID (8 bits)
 * message subtype ID (8 bits)
 * message type counter (16 bits)
 * destination ID (16 bits)
 * time (absolute time) variable
 * spare optional
 * </pre>
 * 
 * In this class we are interested in the time and the sequence count.
 *
 * @author nm
 *
 */
public class PusPacketPreprocessor implements PacketPreprocessor {
    final static Logger log = LoggerFactory.getLogger(PusPacketPreprocessor.class);

    EventProducer eventProducer;
    ErrorDetectionWordCalculator errorDetectionCalculator;
    TimeService timeService;
    
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
        timeService = YamcsServer.getTimeService(yamcsInstance);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setRepeatedEventReduction(true, 10000);
        eventProducer.setSource(this.getClass().getSimpleName());

        configureErrorDetection(config);
        configureTimeDecoder(config);

    }

    void configureErrorDetection(Map<String, Object> config) {
        if (config != null && config.containsKey(Constants.CONFIG_KEY_ERROR_DETECTION)) {
            Map<String, Object> c = YConfiguration.getMap(config, Constants.CONFIG_KEY_ERROR_DETECTION);
            String type = YConfiguration.getString(c, "type");
            if ("CRC-16-CCIIT".equalsIgnoreCase(type)) {
                errorDetectionCalculator = new CrcCciitCalculator(c);
            } else {
                throw new ConfigurationException(
                        "Unknown errorDetectionWord type '" + type + "': supported types are 16-SUM and CRC-16-CCIIT");
            }
        } else {
            errorDetectionCalculator = new CrcCciitCalculator();
        }
        log.debug("Using error detection {}", errorDetectionCalculator);
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
