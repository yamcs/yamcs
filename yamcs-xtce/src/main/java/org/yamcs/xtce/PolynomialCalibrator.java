package org.yamcs.xtce;

import java.util.Arrays;

/**
 * A calibration type where a curve in a raw vs calibrated plane is described using a set of polynomial coefficients.
 * Raw values are converted to calibrated values by finding a position on the curve corresponding to the raw value.
 * The first coefficient belongs with the X^0 term, the next coefficient belongs to the X^1 term and so on.
 * 
 * @author nm
 *
 */
public class PolynomialCalibrator implements Calibrator {
    private static final long serialVersionUID = 3L;
    double[] coefficients;

    public PolynomialCalibrator(double[] coefficients) {
        this.coefficients = coefficients;
    }

    public String toString() {
        return "PolynomialCalibrator" + Arrays.toString(coefficients);
    }

    public double[] getCoefficients() {
        return coefficients;
    }
}
