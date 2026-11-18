package org.yamcs.algo;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataEncoder;
import org.yamcs.mdb.CommandEncodingException;
import org.yamcs.mdb.TcProcessingContext;
import org.yamcs.parameter.TimestampValue;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.AbstractPacketPreprocessor.TimeDecoderType;
import org.yamcs.tctm.AbstractPacketPreprocessor.TimeEpochs;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * Can be used in BinaryArgumentEncoding to encode absolute times directly to binary data.
 * <p>
 * Unlike the pure XTCE based encoder, this one can use a time correlation service to convert a Yamcs time to an
 * on-board time.
 * CUC layout is configured with pField and pFieldCont. Without TCO, the time is
 * shifted to the configured epoch before CUC encoding. With TCO, the correlated
 * raw onboard counter is encoded directly.
 */
public class TimeBinaryEncoder extends AbstractDataEncoder {

    final CucTimeEncoder timeEncoder;
    protected long customEpoch;
    protected boolean customEpochIncludeLeapSecond;
    final protected TimeEpochs timeEpoch;
    final TimeCorrelationService tcoService;
    final AlgorithmExecutionContext ctx;

    public TimeBinaryEncoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Map<String, Object> conf) {
        this(alg, ctx, conf, findTcoService(ctx, YConfiguration.wrap(conf)));
    }

    TimeBinaryEncoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Map<String, Object> conf,
            TimeCorrelationService tcoService) {
        YConfiguration yc = YConfiguration.wrap(conf);
        this.ctx = ctx;
        TimeDecoderType type = yc.getEnum("type", TimeDecoderType.class, TimeDecoderType.CUC);

        timeEncoder = switch (type) {
        case CUC -> {
            boolean implicitPField = yc.containsKey("implicitPField")
                    ? yc.getBoolean("implicitPField") : yc.getBoolean("implicitPfield", true);
            int pField = getPField(yc, "pField", "pfield", -1);
            int pFieldCont = getPField(yc, "pFieldCont", "pfieldCont", -1);
            yield new CucTimeEncoder(pField, pFieldCont, implicitPField);
        }
        default -> {
            throw new UnsupportedOperationException("unknown time encoder type " + type);
        }
        };
        timeEpoch = yc.getEnum("epoch", TimeEpochs.class, TimeEpochs.GPS);
        if (timeEpoch == TimeEpochs.CUSTOM) {
            customEpochIncludeLeapSecond = yc.getBoolean("timeIncludesLeapSeconds", true);
            customEpoch = TimeEncoding.parse(yc.getString("epochUTC"));
            if (!customEpochIncludeLeapSecond) {
                customEpoch = TimeEncoding.toUnixMillisec(customEpoch);
            }
        }
        this.tcoService = tcoService;
    }

    // Accept old MDB configuration names when loading previously generated XTCE.
    static int getPField(YConfiguration config, String name, String legacyName, int defaultValue) {
        return config.containsKey(name) ? config.getInt(name) : config.getInt(legacyName, defaultValue);
    }

    static TimeCorrelationService findTcoService(AlgorithmExecutionContext ctx, YConfiguration yc) {
        if (!yc.containsKey("tcoService")) {
            return null;
        }
        String name = yc.getString("tcoService");
        String instance = ctx.getProcessorData().getYamcsInstance();
        var serverInstance = YamcsServer.getServer().getInstance(instance);
        TimeCorrelationService service = serverInstance == null ? null
                : serverInstance.getService(TimeCorrelationService.class, name);
        if (service == null) {
            throw new ConfigurationException("Cannot find a time correlation service with name " + name);
        }
        return service;
    }

    private long shiftToEpoch(long t) {
        return switch (timeEpoch) {
        case GPS -> TimeEncoding.toGpsTimeMillisec(t);
        case J2000 -> TimeEncoding.toJ2000Millisec(t);
        case TAI -> TimeEncoding.toTaiMillisec(t);
        case UNIX -> TimeEncoding.toUnixMillisec(t);
        case NONE -> t;
        case CUSTOM -> customEpochIncludeLeapSecond ? t - customEpoch : TimeEncoding.toUnixMillisec(t) - customEpoch;
        default -> throw new IllegalStateException("Unknown epoch " + timeEpoch);
        };
    }

    @Override
    public void encodeRaw(DataEncoding de, Value rawValue, BitBuffer buffer, TcProcessingContext ctx) {
        if (rawValue instanceof TimestampValue tv) {
            long t = tv.getTimestampValue();
            if (buffer.getPosition() % 8 != 0) {
                throw new CommandEncodingException(ctx, "Can only encode times at byte boundaries");
            }

            if (tcoService == null) {
                try {
                    int length = timeEncoder.encode(shiftToEpoch(t), buffer.array(), buffer.offset() + buffer.getPosition() / 8);
                    buffer.setPosition(buffer.getPosition() + 8 * length);
                } catch (IllegalArgumentException e) {
                    throw new CommandEncodingException(ctx, e.getMessage());
                }
            } else {
                long obt = tcoService.getObt(t);
                if (obt == Long.MIN_VALUE) {
                    throw new CommandEncodingException(ctx, "Time correlation coefficients not available");
                }
                try {
                    int length = timeEncoder.encodeRaw(obt, buffer.array(), buffer.offset() + buffer.getPosition() / 8);
                    buffer.setPosition(buffer.getPosition() + 8 * length);
                } catch (IllegalArgumentException e) {
                    throw new CommandEncodingException(ctx, e.getMessage());
                }
            }
        } else {
            throw new CommandEncodingException(ctx, "Cannot encode values of type " + rawValue.getType());
        }

    }
}
