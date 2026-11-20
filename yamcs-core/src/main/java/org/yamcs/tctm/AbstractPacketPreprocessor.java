package org.yamcs.tctm;

import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CdsTimeDecoder;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.time.FixedSizeTimeDecoder;
import org.yamcs.time.Float64TimeDecoder;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.time.TimeDecoder;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * This class provides some common facilities for the packet preprocessors. Options:
 * 
 * <pre>
 *   dataLinks:
 *   ...
 *      packetPreprocessor: org.yamcs.tctm.concrete_classname
 *      packetPreprocessorArgs:
 *          byteOrder: LITTLE_ENDIAN
 *          checkSequence: true
 *          timeEncoding:
 *              epoch: CUSTOM
 *              epochUTC: 1970-01-01T00:00:00Z
 *              timeIncludesLeapSeconds: false
 * 
 * </pre>
 * 
 * The {@code byteOrder} option (default is {@code BIG_ENDIAN}) is used by some implementing classes to decode parts of
 * the header.
 * <p>
 * The {@code checkSequence} option (default is true) can configure the implementing classes to raise an event if the
 * sequence count is not continuous (this indicates packet loss).
 * <p>
 * The {@code timeEncoding} is used to convert the extracted time to Yamcs time.
 * 
 * {@code epoch} can be one of TAI, J2000, UNIX, GPS, CUSTOM.
 * <p>
 * If CUSTOM is specified, the {@code epochUTC} has to be used to specify the UTC time which is used as an epoch (UTC is
 * used here loosely because strictly speaking UTC has been only introduced in 1972 so it does not make sense for the
 * times before).
 * <p>
 * The time read from the packet is interpreted as delta from {@code epochUTC}.
 * <p>
 * If {@code timeIncludesLeapSeconds} is {@code true} (default), the delta time is considered as having the leap seconds
 * included (practically it is the real time that passed).
 * <p>
 * TAI, J2000 and GPS have the leap seconds included, UNIX does not.
 * <p>
 * The example above is equivalent with:
 * 
 * <pre>
 * timeEncoding:
 *    epoch: UNIX
 * </pre>
 * 
 * If this option is not configured, the default will be different for each pre-processor.
 * <p>
 * The CDS time decoder ({@code type: CDS}) counts UTC days, so it does not use {@code epoch}, {@code epochUTC} and
 * {@code timeIncludesLeapSeconds}. It requires instead {@code epochDate}: the date of day 0 (for example
 * {@code 1958-01-01}), or {@code NONE} for a raw on-board time converted by the time correlation service.
 *
 * @author nm
 *
 */
public abstract class AbstractPacketPreprocessor implements PacketPreprocessor {

    public static enum TimeEpochs {
        TAI, J2000, UNIX, GPS, CUSTOM, NONE
    };

    public static enum TimeDecoderType {
        CUC, FIXED, FLOAT64, CDS
    }

    protected static final String CONFIG_KEY_ERROR_DETECTION = "errorDetection";
    protected static final String CONFIG_KEY_TIME_ENCODING = "timeEncoding";
    public static final String CONFIG_KEY_TCO_SERVICE = "tcoService";
    protected static final String CONFIG_KEY_BYTE_ORDER = "byteOrder";
    protected static final String CONFIG_KEY_CHECK_SEQUENCE = "checkSequence";

    protected static final String ETYPE_CORRUPTED_PACKET = "CORRUPTED_PACKET";

    // which error detection algorithm to use (null = no checksum)
    protected ErrorDetectionWordCalculator errorDetectionCalculator;
    protected EventProducer eventProducer;
    protected TimeService timeService;

    protected boolean checkForSequenceDiscontinuity = true;

    // used by some preprocessors to convert the generation time in the packet to yamcs time
    protected TimeEpochs timeEpoch;

    // if timeEpoch is CUSTOM, the following two are used
    // customEpoch is a Yamcs instant if customEpochIncludeLeapSecond = true
    // and is a unix time if customEpochIncludeLeapSecond=false
    protected long customEpoch;
    protected boolean customEpochIncludeLeapSecond;

    // for the CDS time decoder: the epochDate as number of days since 1970-01-01. The timeEpoch is not used with CDS,
    // except that it is NONE when the epochDate is NONE.
    protected long cdsEpochDay;

    protected TimeDecoder timeDecoder = null;
    protected ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

    protected TimeCorrelationService tcoService;
    /**
     * If true, do not extract time from packets but use the local generation time.
     * <p>
     * It is a good idea to set the {@link TmPacket#setLocalGenTimeFlag()} flag to indicate it.
     * <p>
     * The flag has to be set by the pre-processor!
     */
    protected boolean useLocalGenerationTime;
    final protected Log log;

    protected AbstractPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        log = new Log(this.getClass(), yamcsInstance);

        // Before anything else
        byteOrder = getByteOrder(config);

        errorDetectionCalculator = getErrorDetectionWordCalculator(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        timeService = YamcsServer.getTimeService(yamcsInstance);
        this.checkForSequenceDiscontinuity = config.getBoolean(CONFIG_KEY_CHECK_SEQUENCE, true);

        configureTimeDecoder(config);

        if (config.containsKey(CONFIG_KEY_TCO_SERVICE)) {
            String tcoServiceName = config.getString(CONFIG_KEY_TCO_SERVICE);
            tcoService = YamcsServer.getServer().getInstance(yamcsInstance).getService(TimeCorrelationService.class,
                    tcoServiceName);
            if (tcoService == null) {
                throw new ConfigurationException(
                        "Cannot find a time correlation service with name " + tcoServiceName);
            }
        }
    }

    private void configureTimeDecoder(YConfiguration config) {
        if (config != null) {
            useLocalGenerationTime = config.getBoolean("useLocalGenerationTime", false);
        }

        if (!useLocalGenerationTime && config != null && config.containsKey(CONFIG_KEY_TIME_ENCODING)) {
            YConfiguration c = config.getConfig(CONFIG_KEY_TIME_ENCODING);
            timeDecoder = getDecoder(c);
            if (timeDecoder instanceof CdsTimeDecoder) {
                configureCdsEpoch(c, config.containsKey(CONFIG_KEY_TCO_SERVICE));
            } else {
                timeEpoch = c.getEnum("epoch", TimeEpochs.class, TimeEpochs.GPS);
            }
            if (timeEpoch == TimeEpochs.CUSTOM) {
                customEpochIncludeLeapSecond = c.getBoolean("timeIncludesLeapSeconds", true);
                String epochs = c.getString("epochUTC");
                customEpoch = TimeEncoding.parse(epochs);
                if (!customEpochIncludeLeapSecond) {
                    customEpoch = TimeEncoding.toUnixMillisec(customEpoch);
                }
            }
            log.debug("Using time decoder {}", timeDecoder);
        }

    }

    /**
     * CDS counts UTC days, so the time scale is fixed and only the date of day 0 is configured, with the
     * {@code epochDate} option. {@code epochDate: NONE} means that the time is a raw on-board count passed to the time
     * correlation service.
     */
    private void configureCdsEpoch(YConfiguration c, boolean hasTcoService) {
        for (String key : new String[] { "epoch", "epochUTC", "timeIncludesLeapSeconds" }) {
            if (c.containsKey(key)) {
                throw new ConfigurationException(c.getPath(), "the option '" + key
                        + "' cannot be used with the CDS time decoder; use 'epochDate' to set the date of day 0");
            }
        }
        if (!c.containsKey("epochDate")) {
            throw new ConfigurationException(c.getPath(),
                    "the CDS time decoder requires the option 'epochDate' (a date such as 1958-01-01, or NONE)");
        }
        Object o = c.get("epochDate");
        if ("NONE".equalsIgnoreCase(String.valueOf(o))) {
            if (!hasTcoService) {
                throw new ConfigurationException(c.getPath(),
                        "'epochDate: NONE' requires a time correlation service, set with the 'tcoService' option");
            }
            timeEpoch = TimeEpochs.NONE;
            return;
        }
        LocalDate epochDate;
        if (o instanceof Date d) {
            // an unquoted date in the YAML file
            long millis = d.getTime();
            if (Math.floorMod(millis, CdsTimeDecoder.MILLIS_PER_DAY) != 0) {
                throw new ConfigurationException(c.getPath(), "epochDate has to be a date without a time");
            }
            epochDate = LocalDate.ofEpochDay(Math.floorDiv(millis, CdsTimeDecoder.MILLIS_PER_DAY));
        } else if (o instanceof String s) {
            try {
                epochDate = LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new ConfigurationException(c.getPath(),
                        "invalid epochDate '" + s + "': expected a date such as 1958-01-01, or NONE");
            }
        } else {
            throw new ConfigurationException(c.getPath(),
                    "invalid epochDate " + o + ": expected a date such as 1958-01-01, or NONE");
        }
        cdsEpochDay = epochDate.toEpochDay();
    }

    private TimeDecoder getDecoder(YConfiguration c) {
        TimeDecoderType type = c.getEnum("type", TimeDecoderType.class, getDefaultDecoderType());

        TimeDecoder timeDecoder;

        switch (type) {
        case CUC:
            int implicitPField = c.getInt("implicitPField", -1);
            int implicitPFieldCont = c.getInt("implicitPFieldCont", -1);
            timeDecoder = new CucTimeDecoder(implicitPField, implicitPFieldCont);
            break;
        case CDS:
            timeDecoder = new CdsTimeDecoder(c.getInt("implicitPField", -1));
            break;
        case FIXED:
            int size = c.getInt("size", 8);
            if (size != 4 && size != 8) {
                throw new ConfigurationException(
                        "Unsupported size " + size + " for fixed decoder. Only 4 and 8 bytes supported");
            }
            double multiplier = c.getDouble("multiplier", 1);
            timeDecoder = new FixedSizeTimeDecoder(byteOrder, size, multiplier);
            break;
        case FLOAT64:
            timeDecoder = new Float64TimeDecoder(byteOrder);
            break;
        default:
            throw new UnsupportedOperationException("unknown time decoder type " + type);
        }

        return timeDecoder;
    }

    public void verifyCrc(TmPacket tmPacket) {
        if (errorDetectionCalculator == null) {
            return;
        }

        boolean corrupted = false;
        byte[] packet = tmPacket.getPacket();

        int n = packet.length;
        int computedCheckword;
        try {
            computedCheckword = errorDetectionCalculator.compute(packet, 0, n - 2);
            int packetCheckword = ByteArrayUtils.decodeUnsignedShort(packet, n - 2);
            if (packetCheckword != computedCheckword) {
                eventProducer.sendWarning("Corrupted packet received, computed checkword: " + computedCheckword
                        + "; packet checkword: " + packetCheckword);
                corrupted = true;
            }
        } catch (IllegalArgumentException e) {
            eventProducer.sendWarning("Error when computing checkword: " + e);
            corrupted = true;
        }
        if (corrupted) {
            tmPacket.setInvalid(true);
        }
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
                return new CrcCciitCalculator();
            } else {
                return new CrcCciitCalculator(crcConf);
            }
        } else if ("ISO-16".equalsIgnoreCase(type)) {
            return new Iso16CrcCalculator();
        } else if ("NONE".equalsIgnoreCase(type)) {
            return null;
        } else {
            throw new ConfigurationException(
                    "Unknown errorDetectionWord type '" + type
                            + "': supported types are 16-SUM, CRC-16-CCIIT and ISO-16 (or NONE)");
        }
    }

    /**
     * Decodes the time at the offset using the time decoder, sets and verifies the generation time depending on the
     * timeEpoch and the tcoService.
     * <p>
     * If there is any exception when decoding the time, the packet is marked as invalid.
     * <p>
     * It is important this is called only for realtime packets.
     *
     * @param tmPacket
     * @param offset
     */
    protected void setRealtimePacketTime(TmPacket tmPacket, int offset) {
        if (useLocalGenerationTime) {
            tmPacket.setGenerationTime(tmPacket.getReceptionTime());
            tmPacket.setLocalGenTimeFlag();
            return;
        }

        byte[] packet = tmPacket.getPacket();
        try {
            if (isRawTime()) {
                long obt = timeDecoder.decodeRaw(packet, offset);
                tmPacket.setObt(obt);
                tcoService.timestamp(obt, tmPacket);
            } else {
                long gentime;
                if (timeDecoder instanceof CdsTimeDecoder cdsDecoder) {
                    gentime = cdsToInstant(cdsDecoder.decodeDayTime(packet, offset));
                } else {
                    gentime = shiftFromEpoch(timeDecoder.decode(packet, offset));
                }
                tmPacket.setGenerationTime(gentime);
                if (tcoService != null) {
                    tcoService.verify(tmPacket);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract time from the packet", e);
            eventProducer.sendWarning("Failed to extract time from packet: " + e);
            tmPacket.setInvalid(true);
        }
    }

    /**
     * Returns true if the decoded time is a raw on-board time, to be converted by the time correlation service.
     */
    private boolean isRawTime() {
        if (timeDecoder instanceof CdsTimeDecoder) {
            return timeEpoch == TimeEpochs.NONE;
        }
        return timeEpoch == null || timeEpoch == TimeEpochs.NONE;
    }

    /**
     * Converts a CDS day and millisecond of day to a Yamcs instant.
     * <p>
     * CDS counts UTC days from the {@code epochDate} and the millisecond of day goes up to 86400999 during a leap
     * second (CCSDS 301.0-B-4 Annex A). The Yamcs instants include the leap seconds, so the time is the start of the
     * day plus the millisecond of day. A leap second is only accepted on a day that has one.
     */
    protected long cdsToInstant(CdsTimeDecoder.DayTime dt) {
        long dayStart = TimeEncoding.fromUnixMillisec((cdsEpochDay + dt.day()) * CdsTimeDecoder.MILLIS_PER_DAY);
        if (dt.msOfDay() >= CdsTimeDecoder.MILLIS_PER_DAY) {
            long dayLength = TimeEncoding
                    .fromUnixMillisec((cdsEpochDay + dt.day() + 1) * CdsTimeDecoder.MILLIS_PER_DAY) - dayStart;
            if (dt.msOfDay() >= dayLength) {
                throw new IllegalArgumentException("Invalid CDS millisecond of day " + dt.msOfDay() + " for day "
                        + dt.day() + ": that day has no leap second");
            }
        }
        return dayStart + dt.msOfDay();
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
            throw new IllegalStateException("Unknown epoch " + timeEpoch);
        }
    }

    public static ByteOrder getByteOrder(YConfiguration config) {
        String order = config.getString(CONFIG_KEY_BYTE_ORDER, ByteOrder.BIG_ENDIAN.toString());
        if ("BIG_ENDIAN".equalsIgnoreCase(order)) {
            return ByteOrder.BIG_ENDIAN;
        } else if ("LITTLE_ENDIAN".equalsIgnoreCase(order)) {
            return ByteOrder.LITTLE_ENDIAN;
        } else {
            throw new ConfigurationException(
                    "Invalid '" + order + "' byte order specified. Use one of BIG_ENDIAN or LITTLE_ENDIAN");
        }
    }

    /**
     * return the default decoder type. The subclasses may override this for compatibility with old Yamcs releases
     */
    protected TimeDecoderType getDefaultDecoderType() {
        return TimeDecoderType.CUC;
    }

    public boolean checkForSequenceDiscontinuity() {
        return checkForSequenceDiscontinuity;
    }

    @Override
    public void checkForSequenceDiscontinuity(boolean checkForSequenceDiscontinuity) {
        this.checkForSequenceDiscontinuity = checkForSequenceDiscontinuity;
    }

}
