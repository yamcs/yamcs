package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Context calibrations are applied when the ContextMatch is true. 
 * Context calibrators override Default calibrators
 * 
 * @author nm
 *
 */
public class ContextCalibrator implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Calibrator calibrator;
    private MatchCriteria context;
   
    public ContextCalibrator( MatchCriteria context, Calibrator calibrator) {
        this.calibrator = calibrator;
        this.context = context;
    }
    
    public Calibrator getCalibrator() {
        return calibrator;
    }
    public void setCalibrator(Calibrator calibrator) {
        this.calibrator = calibrator;
    }
    public MatchCriteria getContextMatch() {
        return context;
    }
    public void setContext(MatchCriteria context) {
        this.context = context;
    }
    @Override
    public String toString() {
        return "ContextCalibrator [ context:" + context +", calibrator:" + calibrator  + "]";
    }
}
