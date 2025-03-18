package org.yamcs.mdb;

import org.yamcs.parameter.Value;

/**
 * Interface for calibrators (raw to engineering conversion) used for telemetry or de-calibrators(engineering to raw
 * conversion) used for commands.
 * <p>
 * Prior to Yamcs 5.12 only numerical calibrations were possible; starting with Yamcs 5.12 it is possible to convert any
 * type to any type. The numerical calibrators have been moved to {@link NumericCalibratorProc} class.
 * <p>
 * Also starting with Yamcs 5.12, the calibrators get access to the {@link ProcessingContext} allowing them to lookup other
 * parameters that may be required to perform the calibration.
 * <p>
 * The calibrators know the target data type, and the value they generate should conform to the expected type.
 */
public interface CalibratorProc {
    /**
     * Calibrate the value v
     * <p>
     * 
     * @throws XtceProcessingException
     *             if the calibrator cannot calibrate the input value v or if a required input (that should be in the
     *             pdata) is not present
     * 
     */
    public abstract Value calibrate(Value v, ProcessingContext pctx) throws XtceProcessingException;
}
