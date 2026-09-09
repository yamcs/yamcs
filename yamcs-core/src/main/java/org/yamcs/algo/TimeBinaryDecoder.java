package org.yamcs.algo;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
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
        YConfiguration yc = YConfiguration.wrap(conf);
        this.ctx = ctx;
        TimeDecoderType type = yc.getEnum("type", TimeDecoderType.class, TimeDecoderType.CUC);

        timeDecoder = switch (type) {
            case CUC -> {
                int implicitPField = yc.getInt("implicitPField", -1);
                int implicitPFieldCont = yc.getInt("implicitPFieldCont", -1);
                yield new CucTimeDecoder(implicitPField, implicitPFieldCont);
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

        if (yc.containsKey("tcoService")) {
            String tcoServiceName = yc.getString("tcoService");
            String yamcsInstance = ctx.getProcessorData().getYamcsInstance();
            tcoService = YamcsServer.getServer().getInstance(yamcsInstance)
                    .getService(TimeCorrelationService.class, tcoServiceName);
            if (tcoService == null) {
                throw new ConfigurationException(
                        "Cannot find a time correlation service with name " + tcoServiceName);
            }
        } else {
            tcoService = null;
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
        if (timeEpoch == null || timeEpoch == TimeEpochs.NONE) {
            obt = timeDecoder.decodeRaw(suppl);

            if (tcoService != null) {
                long pGenTime = pcontext.getGenerationTime();
                Instant hisTime = tcoService.getHistoricalTime(Instant.get(pGenTime), obt);
                
                // FIXME: How to handle this?
                obt = (hisTime == Instant.INVALID_INSTANT) ? obt: hisTime.getMillis();
            }
        } else {
            obt = timeDecoder.decode(suppl);
            obt = shiftFromEpoch(obt);
        }

        return ValueUtility.getTimestampValue(obt);
    }
}
