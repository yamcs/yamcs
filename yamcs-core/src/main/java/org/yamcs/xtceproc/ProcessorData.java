package org.yamcs.xtceproc;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;

/**
 * Holds information related and required for XTCE processing. 
 * It is separated from Processor because it has to be usable when not a full blown processor is available (e.g. XTCE packet processing)
 * 
 *  For the moment is just calibrators used - but in the future could be any deviation from loaded XTCE DB (e.g. alarm definitions and others).
 *  
 *  Ultimately should be connected with the ParameterCache for things that depend on the history (contextual alarms, contextual calibrations, algorithms, etc)
 *  
 *
 */
public class ProcessorData {
    /**
     * converts raw values into engineering values
     */
    final ParameterTypeProcessor parameterTypeProcessor = new ParameterTypeProcessor(this);

    private Map<DataEncoding, CalibratorProc> calibrators = new HashMap<>();

    /**
     * returns a calibrator processor for the given data encoding. 
     * Can be null if the DataEncoding does not define a calibrator.
     * 
     * @param de
     * @return a calibrator processor or null
     */
    public CalibratorProc getCalibrator(DataEncoding de) {
        CalibratorProc calibrator = calibrators.get(de);
        if(calibrator==null) {
            if(de instanceof IntegerDataEncoding) {
                calibrator = getCalibrator(((IntegerDataEncoding) de).getDefaultCalibrator());
            } else if(de instanceof  FloatDataEncoding) {
                calibrator = getCalibrator(((FloatDataEncoding) de).getDefaultCalibrator());
            } else {
                throw new IllegalStateException("Calibrators not supported for: "+de);
            }
            if(calibrator!=null) {
                calibrators.put(de, calibrator);
            }
        }
        return calibrator;
    }

    public CalibratorProc getDecalibrator(DataEncoding de) {
        return getCalibrator(de);
    }

    private CalibratorProc getCalibrator(Calibrator c) {
        if(c==null) {
            return null;
        }
        if(c instanceof PolynomialCalibrator) {
            return new PolynomialCalibratorProc((PolynomialCalibrator) c);
        } else if(c instanceof SplineCalibrator) {
            return new SplineCalibratorProc((SplineCalibrator) c);
        }  else {
            throw new IllegalStateException("No calibrator processor for "+c);
        }
    }

}
