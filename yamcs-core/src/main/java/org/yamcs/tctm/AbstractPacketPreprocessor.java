package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.tctm.ccsds.CrcCciitCalculator;
import org.yamcs.time.TimeService;

public abstract class AbstractPacketPreprocessor implements PacketPreprocessor {
    static final String CONFIG_KEY_ERROR_DETECTION = "errorDetection";
    static final String ETYPE_CORRUPTED_PACKET = "CORRUPTED_PACKET";
    
    // which error detection algorithm to use (null = no checksum)
    protected ErrorDetectionWordCalculator errorDetectionCalculator;
    protected EventProducer eventProducer;
    protected TimeService timeService;

    protected AbstractPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        errorDetectionCalculator = getErrorDetectionWordCalculator(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }
    static ErrorDetectionWordCalculator getErrorDetectionWordCalculator(Map<String, Object> config) {
        if ((config == null) || !config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            return null;
        }

        Map<String, Object> c = YConfiguration.getMap(config, CONFIG_KEY_ERROR_DETECTION);
        String type = YConfiguration.getString(c, "type");
        if ("16-SUM".equalsIgnoreCase(type)) {
            return new Running16BitChecksumCalculator();
        } else if ("CRC-16-CCIIT".equalsIgnoreCase(type)) {
            return new CrcCciitCalculator(c);
        } else {
            throw new ConfigurationException(
                    "Unknown errorDetectionWord type '" + type + "': supported types are 16-SUM and CRC-16-CCIIT");
        }
    }
}
