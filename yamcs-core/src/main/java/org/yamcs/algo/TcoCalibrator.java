package org.yamcs.algo;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AlgorithmCalibratorProc;
import org.yamcs.mdb.DataEncodingUtils;
import org.yamcs.mdb.ProcessingContext;
import org.yamcs.mdb.XtceProcessingException;
import org.yamcs.parameter.SInt32Value;
import org.yamcs.parameter.SInt64Value;
import org.yamcs.parameter.TimestampValue;
import org.yamcs.parameter.UInt32Value;
import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.IntegerDataEncoding;

/**
 * Calibrator that converts Absolute Times to/from integer using a time correlation service.
 * <p>
 * Works both in calibration (integer to timestamp) for parameters and decalibration (timestamp to integer) for command
 * arguments.
 * <p>
 * There is configuration parameter shiftBits that allows shifting the raw value to the left by that number of bits when
 * converting to the obt that is then passed to the time correlation service. Reversely the obt retrieved from the time
 * correlation service is shifted to the right to get the raw value.
 * <p>
 * The reason for the shiftBits is that it has been observed in some ESA missions that the time correlation service
 * works with higher precision than the timestamps used in TM and TC.
 * 
 */
public class TcoCalibrator implements AlgorithmCalibratorProc {
    TimeCorrelationService tcoService;
    AlgorithmExecutionContext ctx;
    IntegerDataEncoding dataEncoding;
    int shiftBits;

    public void init(CustomAlgorithm alg, AlgorithmExecutionContext ctx, YConfiguration conf,
            BaseDataType dtype) {
        this.ctx = ctx;
        if (dtype.getEncoding() instanceof IntegerDataEncoding ide) {
            this.dataEncoding = ide;
        } else {
            throw new XtceProcessingException(
                    "Cannot use the TimeIntegerCalibrator for data encoding " + dtype.getEncoding());
        }

        String tcoServiceName = conf.getString("tcoService");
        String yamcsInstance = ctx.getProcessorData().getYamcsInstance();
        tcoService = YamcsServer.getServer().getInstance(yamcsInstance)
                .getService(TimeCorrelationService.class, tcoServiceName);
        if (tcoService == null) {
            throw new ConfigurationException(
                    "Cannot find a time correlation service with name " + tcoServiceName);
        }
        shiftBits = conf.getInt("shiftBits", 0);
    }

    @Override
    public Value calibrate(Value rawValue, ProcessingContext pctx) throws XtceProcessingException {
        if (rawValue instanceof UInt32Value) {
            return calibrate(rawValue.getUint32Value(), pctx);
        } else if (rawValue instanceof UInt64Value) {
            return calibrate(rawValue.getUint64Value(), pctx);
        } else if (rawValue instanceof SInt32Value) {
            return calibrate(rawValue.getSint32Value(), pctx);
        } else if (rawValue instanceof SInt64Value) {
            return calibrate(rawValue.getSint64Value(), pctx);
        } else {
            throw new XtceProcessingException("Cannot calibrate/decalibrate values of type " + rawValue.getClass());
        }
    }

    @Override
    public Value decalibrate(Value rawValue, ProcessingContext pctx) {
        if (rawValue instanceof TimestampValue) {
            long obt = tcoService.getObt(rawValue.getTimestampValue());
            obt >>= shiftBits;
            return DataEncodingUtils.getRawIntegerValue(dataEncoding, obt);
        } else {
            throw new XtceProcessingException("Cannot calibrate/decalibrate values of type " + rawValue.getClass());
        }
    }

    private Value calibrate(long obt, ProcessingContext pctx) {
        obt <<= shiftBits;
        Instant t = tcoService.getHistoricalTime(Instant.get(pctx.getGenerationTime()), obt);
        return new TimestampValue(t.getMillis(), t.getPicos());
    }
}
