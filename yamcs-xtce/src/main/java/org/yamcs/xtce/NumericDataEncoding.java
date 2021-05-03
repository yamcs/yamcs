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
    public void setDefaultCalibrator(Calibrator calibrator);
    public void setContextCalibratorList(List<ContextCalibrator> contextCalibratorList);
    
    public static interface Builder<T extends Builder<T>> {
        T setDefaultCalibrator(Calibrator defaultCalibrator);
        T setContextCalibratorList(List<ContextCalibrator> contextCalibrator);
    }
}
