package org.yamcs.xtce;

import java.util.List;

/**
 * Interface that makes it easier to work with either of FloatDataEncoding or IntegerDataEncoding
 * @author nm
 *
 */
public interface NumericDataEncoding {
    public List<ContextCalibrator> getContextCalibratorList();
    public Calibrator getDefaultCalibrator();
}
