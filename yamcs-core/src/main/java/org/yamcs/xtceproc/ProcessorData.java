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
 * Holds information related anre required for XTCE processing.
 * 
 *  For the moment is just calibrators used - but in the future could be any deviation from loaded XTCE DB (e.g. alarm definitions and others).
 *  
 *  Ultimately should be somehow connected with AlgorithmExecutionContext and also with the ParameterCache
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
                throw new IllegalStateException("Unsupported integer encoding of type: "+de);
            }
            calibrators.put(de, calibrator);
        }
        return calibrator;
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

    public CalibratorProc getDecalibrator(DataEncoding de) {
        CalibratorProc calibrator = calibrators.get(de);
        if(calibrator==null) {
            if(de instanceof IntegerDataEncoding) {
                calibrator = getCalibrator(((IntegerDataEncoding) de).getDefaultCalibrator());
            } else if(de instanceof  FloatDataEncoding) {
                calibrator = getCalibrator(((FloatDataEncoding) de).getDefaultCalibrator());
            } else {
                throw new IllegalStateException("Unsupported integer encoding of type: "+de);
            }
            calibrators.put(de, calibrator);
        }
        return calibrator;
    }
}
