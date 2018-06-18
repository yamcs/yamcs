package org.yamcs.xtce;

public class JavaExpressionCalibrator implements Calibrator {
   
    private static final long serialVersionUID = 1L;
    private final String javaFormula;

    public JavaExpressionCalibrator(String javaFormula) {
        this.javaFormula = javaFormula;
    }
    public String getFormula() {
        return javaFormula;
    }
    
    @Override
    public String toString() {
        return "JavaExpressionCalibrator [" + javaFormula + "]";
    }
}
