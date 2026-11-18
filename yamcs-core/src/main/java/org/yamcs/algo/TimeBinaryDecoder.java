package org.yamcs.algo;

import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataDecoder;
import org.yamcs.mdb.ContainerProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.AbstractPacketPreprocessor.TimeDecoderType;
import org.yamcs.tctm.AbstractPacketPreprocessor.TimeEpochs;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ByteSupplier;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * Can be used in BinaryParameterEncoding to decode binary data directly to absolute times
 * <p>
 * Unlike the pure XTCE based decoder, this one can use a time correlation service to convert between an on-board time
 * and Yamcs time.
 * CUC layout is configured with pField and pFieldCont, with no implicit flag.
 * With TCO, the raw onboard counter is correlated directly; otherwise the CUC
 * value is interpreted relative to the configured epoch.
 */
public class TimeBinaryDecoder extends AbstractDataDecoder {

    final CucTimeDecoder timeDecoder;
    final protected TimeEpochs timeEpoch;

    // if timeEpoch is CUSTOM, the following two are used
    // customEpoch is a Yamcs instant if customEpochIncludeLeapSecond = true
    // and is a unix time if customEpochIncludeLeapSecond=false
    protected long customEpoch;
    protected boolean customEpochIncludeLeapSecond;

    final TimeCorrelationService tcoService;
    final AlgorithmExecutionContext ctx;

    public TimeBinaryDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Map<String, Object> conf) {
        this(alg, ctx, conf, TimeBinaryEncoder.findTcoService(ctx, YConfiguration.wrap(conf)));
    }

    TimeBinaryDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Map<String, Object> conf,
            TimeCorrelationService tcoService) {
        YConfiguration yc = YConfiguration.wrap(conf);
        this.ctx = ctx;
        TimeDecoderType type = yc.getEnum("type", TimeDecoderType.class, TimeDecoderType.CUC);

        timeDecoder = switch (type) {
            case CUC -> {
                int pField = TimeBinaryEncoder.getPField(yc, "pField", "implicitPField", -1);
                int pFieldCont = TimeBinaryEncoder.getPField(yc, "pFieldCont", "implicitPFieldCont", -1);
                yield new CucTimeDecoder(pField, pFieldCont);
            }
            default -> {
                throw new UnsupportedOperationException("unknown time decoder type " + type);
            }
        };
        timeEpoch = yc.getEnum("epoch", TimeEpochs.class, TimeEpochs.NONE);
        if (timeEpoch == TimeEpochs.CUSTOM) {
            customEpochIncludeLeapSecond = yc.getBoolean("timeIncludesLeapSeconds", true);
            String epochs = yc.getString("epochUTC");
            customEpoch = TimeEncoding.parse(epochs);
            if (!customEpochIncludeLeapSecond) {
                customEpoch = TimeEncoding.toUnixMillisec(customEpoch);
            }
        }

        this.tcoService = tcoService;
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

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buf) {
        var suppl = new ByteSupplier() {
            @Override
            public byte getAsByte() {
                return buf.getByte();
            }
        };

        long obt;
        if (tcoService != null) {
            long raw = timeDecoder.decodeRaw(suppl);
            Instant time = tcoService.getHistoricalTime(Instant.get(pcontext.getGenerationTime()), raw);
            if (time == Instant.INVALID_INSTANT) {
                throw new IllegalStateException("Time correlation coefficients not available");
            }
            obt = time.getMillis();
        } else if (timeEpoch == TimeEpochs.NONE) {
            obt = timeDecoder.decodeRaw(suppl);
        } else {
            obt = shiftFromEpoch(timeDecoder.decode(suppl));
        }

        return ValueUtility.getTimestampValue(obt);
    }
}
