package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

public abstract class AbstractPacketPreprocessor implements PacketPreprocessor {

    public static enum TimeEpochs {
        TAI, J2000, UNIX, GPS
    };

    protected static final String CONFIG_KEY_ERROR_DETECTION = "errorDetection";
    protected static final String CONFIG_KEY_TIME_ENCODING = "timeEncoding";
    static final String ETYPE_CORRUPTED_PACKET = "CORRUPTED_PACKET";

    // which error detection algorithm to use (null = no checksum)
    protected ErrorDetectionWordCalculator errorDetectionCalculator;
    protected EventProducer eventProducer;
    protected TimeService timeService;
    // used by some preprocessors to convert the generation time in the packet to yamcs time
    protected TimeEpochs timeEpoch;

    protected TimeDecoder timeDecoder = null;

    // if true, do not extract time from packets
    protected boolean useLocalGenerationTime;
    final protected Log log;

    protected AbstractPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        log = new Log(this.getClass(), yamcsInstance);
        errorDetectionCalculator = getErrorDetectionWordCalculator(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        timeService = YamcsServer.getTimeService(yamcsInstance);

        configureTimeDecoder(config);

    }

    void configureTimeDecoder(YConfiguration config) {
        if (config != null) {
            useLocalGenerationTime = config.getBoolean("useLocalGenerationTime", false);
        }

        if (!useLocalGenerationTime && config != null && config.containsKey(CONFIG_KEY_TIME_ENCODING)) {
            YConfiguration c = config.getConfig(CONFIG_KEY_TIME_ENCODING);
            String type = c.getString("type", "CUC");
            if ("CUC".equals(type)) {
                int implicitPField = c.getInt("implicitPField", -1);
                timeDecoder = new CucTimeDecoder(implicitPField);
            } else {
                throw new ConfigurationException("Time encoding of type '" + type
                        + " not supported. Supported: CUC=CCSDS unsegmented time");
            }
            timeEpoch = c.getEnum("epoch", TimeEpochs.class, TimeEpochs.GPS);
        } else {
            timeDecoder = new CucTimeDecoder(-1);
        }
        log.debug("Using time decoder {}", timeDecoder);
    }

    public static ErrorDetectionWordCalculator getErrorDetectionWordCalculator(YConfiguration config) {
        if ((config == null) || !config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            return null;
        }

        YConfiguration c = config.getConfig(CONFIG_KEY_ERROR_DETECTION);
        String type = c.getString("type");
        if ("16-SUM".equalsIgnoreCase(type)) {
            return new Running16BitChecksumCalculator();
        } else if ("CRC-16-CCIIT".equalsIgnoreCase(type)) {
            return new CrcCciitCalculator(c);
        } else {
            throw new ConfigurationException(
                    "Unknown errorDetectionWord type '" + type + "': supported types are 16-SUM and CRC-16-CCIIT");
        }
    }

    protected static long shiftFromEpoch(TimeEpochs epoch, long t) {
        switch (epoch) {
        case GPS:
            return TimeEncoding.fromGpsMillisec(t);
        case J2000:
            return TimeEncoding.fromJ2000Millisec(t);
        case TAI:
            return TimeEncoding.fromTaiMillisec(t);
        case UNIX:
            return TimeEncoding.fromUnixMillisec(t);
        default:
            throw new IllegalStateException("Unknonw epoch " + epoch);
        }
    }
}
