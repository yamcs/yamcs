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
 * when decalibrating (command arguments) Timestamps are always converted to sint64.
 * <p>
 * when calibrating (tm parameters) the raw value
 * <p>
 * This is most useful when using a time correlation service.
 */
public class TcoCalibrator implements AlgorithmCalibratorProc {
    TimeCorrelationService tcoService;
    AlgorithmExecutionContext ctx;
    IntegerDataEncoding dataEncoding;

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
            return DataEncodingUtils.getRawIntegerValue(dataEncoding, obt);
        } else {
            throw new XtceProcessingException("Cannot calibrate/decalibrate values of type " + rawValue.getClass());
        }
    }

    private Value calibrate(long obt, ProcessingContext pctx) {
        Instant t = tcoService.getHistoricalTime(Instant.get(pctx.getGenerationTime()), obt);
        return new TimestampValue(t.getMillis(), t.getPicos());
    }
}
