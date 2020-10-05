package org.yamcs.tctm;

import java.nio.ByteOrder;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
/**
 * This class provides some common facilities for the packet preprocessors.
 * Options:
 * <pre>
 *   dataLinks:
 *   ...
 *      packetPreprocessor: org.yamcs.tctm.concrete_classname
 *      packetPreprocessorArgs:
 *          byteOrder: LITTLE_ENDIAN
 *          timeEncoding:
 *              epoch: CUSTOM
 *              epochUTC: 1970-01-01T00:00:00Z
 *              timeIncludesLeapSeconds: false
 *   
 *  </pre>  
 * 
 * The {@code byteOrder} option (default is {@code BIG_ENDIAN}) is used by some implementing classes to decode parts of the header.
 * <p>
 * The {@code timeEncoding} is used to convert the extracted time to Yamcs time.
 * 
 * {@code epoch} can be one of TAI, J2000, UNIX, GPS, CUSTOM.
 * <p>
 * If CUSTOM is specified, the {@code epochUTC} has to be used to specify the UTC time which is used as an epoch (UTC is
 * used here loosely because strictly speaking UTC has been only introduced in 1972 so it does not make sense for the times before).
 * <p>
 * The time read from the packet is interpreted as delta from {@code epochUTC}.
 * <p>If {@code timeIncludesLeapSeconds} is {@code true} (default), the delta time is considered as having the leap seconds included
 * (practically it is the real time that passed).
 * <p>
 * TAI, J2000 and GPS have the leap seconds included, UNIX does not.
 * <p>
 * The example above is equivalent with:
 * <pre>
 * timeEncoding:
 *    epoch: UNIX
 * </pre>
 * If this option is not configured, the default will be different for each pre-processor.   
 * @author nm
 *
 */
public abstract class AbstractPacketPreprocessor implements PacketPreprocessor {

    public static enum TimeEpochs {
        TAI, J2000, UNIX, GPS, CUSTOM
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

    // if timeEpoch is CUSTOM, the following two are used
    // customEpoch is a Yamcs instant if customEpochIncludeLeapSecond = true 
    // and is a unix time if customEpochIncludeLeapSecond=false
    protected long customEpoch;
    protected boolean customEpochIncludeLeapSecond;

    protected TimeDecoder timeDecoder = null;
    protected ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
    //
    /**
     * If true, do not extract time from packets but use the local generation time.
     * <p>
     * It is a good idea to set the {@link TmPacket#setLocalGenTime()} flag to indicate it.
     * <p>
     * The flag has to be set by the pre-processor!
     */
    protected boolean useLocalGenerationTime;
    final protected Log log;

    protected AbstractPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        log = new Log(this.getClass(), yamcsInstance);
        errorDetectionCalculator = getErrorDetectionWordCalculator(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        timeService = YamcsServer.getTimeService(yamcsInstance);
        configureTimeDecoder(config);

        if (config != null) {
            String order = config.getString("byteOrder", ByteOrder.BIG_ENDIAN.toString());
            if ("BIG_ENDIAN".equalsIgnoreCase(order)) {
                byteOrder = ByteOrder.BIG_ENDIAN;
            } else if ("LITTLE_ENDIAN".equalsIgnoreCase(order)) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
            } else {
                throw new ConfigurationException(
                        "Invalid '" + order + "' byte order specified. Use one of BIG_ENDIAN or LITTLE_ENDIAN");
            }
        }
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
            if (timeEpoch == TimeEpochs.CUSTOM) {
                customEpochIncludeLeapSecond = c.getBoolean("timeIncludesLeapSeconds", true);
                String epochs = c.getString("epochUTC");
                customEpoch = TimeEncoding.parse(epochs);
                if (!customEpochIncludeLeapSecond) {
                    customEpoch = TimeEncoding.toUnixMillisec(customEpoch);
                }
            }
        } else {
            timeEpoch = TimeEpochs.GPS;
            timeDecoder = new CucTimeDecoder(-1);
        }
        log.debug("Using time decoder {}", timeDecoder);
    }

    public static ErrorDetectionWordCalculator getErrorDetectionWordCalculator(YConfiguration config) {
        if ((config == null) || !config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            return null;
        }
        String type;
        YConfiguration crcConf = null;
        if (config.get(CONFIG_KEY_ERROR_DETECTION) instanceof Map<?, ?>) {
            crcConf = config.getConfig(CONFIG_KEY_ERROR_DETECTION);
            type = crcConf.getString("type");
        } else {
            type = config.getString(CONFIG_KEY_ERROR_DETECTION);
        }

        if ("16-SUM".equalsIgnoreCase(type)) {
            return new Running16BitChecksumCalculator();
        } else if ("CRC-16-CCIIT".equalsIgnoreCase(type)) {
            if (crcConf == null) {
                return new CrcCciitCalculator(crcConf);
            } else {
                return new CrcCciitCalculator();
            }
        } else if ("NONE".equalsIgnoreCase(type)) {
            return null;
        } else {
            throw new ConfigurationException(
                    "Unknown errorDetectionWord type '" + type
                            + "': supported types are 16-SUM and CRC-16-CCIIT (or NONE)");
        }
    }

    protected long shiftFromEpoch(long t) {
        switch (timeEpoch) {
        case GPS:
            return TimeEncoding.fromGpsMillisec(t);
        case J2000:
            return TimeEncoding.fromJ2000Millisec(t);
        case TAI:
            return TimeEncoding.fromTaiMillisec(t);
        case UNIX:
            return TimeEncoding.fromUnixMillisec(t);
        case CUSTOM:
            if (customEpochIncludeLeapSecond) {
                return customEpoch + t;
            } else {
                return TimeEncoding.fromUnixMillisec(customEpoch + t);
            }
        default:
            throw new IllegalStateException("Unknonw epoch " + timeEpoch);
        }
    }
}
