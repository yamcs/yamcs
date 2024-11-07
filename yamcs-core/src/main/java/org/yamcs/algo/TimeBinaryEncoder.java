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
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * Can be used in BinaryArgumetEncoding to encode absolute times directoy to binary data
 * <p>
 * Unlike the pure XTCE based encoder, this one can use a time correlation service to convert a Yamcs time to an
 * on-board time.
 */
public class TimeBinaryEncoder extends AbstractDataEncoder {

    final CucTimeEncoder timeEncoder;
    final protected TimeEpochs timeEpoch;
    final TimeCorrelationService tcoService;
    final AlgorithmExecutionContext ctx;

    public TimeBinaryEncoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Map<String, Object> conf) {
        YConfiguration yc = YConfiguration.wrap(conf);
        this.ctx = ctx;
        TimeDecoderType type = yc.getEnum("type", TimeDecoderType.class, TimeDecoderType.CUC);

        timeEncoder = switch (type) {
        case CUC -> {
            boolean implicitPfield = yc.getBoolean("implicitPfield", true);
            int pfield1 = yc.getInt("pfield");
            int pfield2 = yc.getInt("pfieldCont", -1);

            yield new CucTimeEncoder(pfield1, pfield2, implicitPfield);
        }
        default -> {
            throw new UnsupportedOperationException("unknown time encoder type " + type);
        }
        };
        timeEpoch = yc.getEnum("epoch", TimeEpochs.class, TimeEpochs.GPS);
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


    @Override
    public void encodeRaw(DataEncoding de, Value rawValue, BitBuffer buffer, TcProcessingContext ctx) {
        if (rawValue instanceof TimestampValue tv) {
            long t = tv.getTimestampValue();

            if (tcoService == null) {
                if(buffer.getPosition() %8 !=0) {
                    throw new CommandEncodingException(ctx, "Can only encode times at byte boundaries");
                }
                int length = timeEncoder.encode(t, buffer.array(), buffer.offset() + buffer.getPosition() / 8);
                buffer.setPosition(buffer.getPosition() + 8 * length);
            } else {
                long obt = tcoService.getObt(t);
                if (obt == Long.MIN_VALUE) {
                    throw new CommandEncodingException(ctx, "Time correlation coefficients not available");
                }
                int length = timeEncoder.encodeRaw(obt, buffer.array(), buffer.offset() + buffer.getPosition() / 8);
                buffer.setPosition(buffer.getPosition() + 8 * length);
            }
        } else {
            throw new CommandEncodingException(ctx, "Cannot encode values of type " + rawValue.getType());
        }

    }
}
