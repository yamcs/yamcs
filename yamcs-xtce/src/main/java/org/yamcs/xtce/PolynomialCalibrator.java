package org.yamcs.xtce;

import java.util.Arrays;

/**
 * A calibration type where a curve in a raw vs calibrated plane is described using a set of polynomial coefficients.  
 * Raw values are converted to calibrated values by finding a position on the curve corresponding to the raw value. 
 * The first coefficient belongs with the X^0 term, the next coefficient belongs to the X^1 term and so on.
 * @author mache
 *
 */
public class PolynomialCalibrator extends Calibrator {
	private static final long serialVersionUID = 200706050619L;
	double[] coefficients;
	public PolynomialCalibrator(double[] coefficients) {
		this.coefficients=coefficients;
	}
	@Override
	public Double calibrate(double d) {
		double val=0;
		for(int i=coefficients.length-1;i>=0;i--) {
				val=d*val+coefficients[i];
		}
		return val;
	}
	
	@Override
    public String toString() {
		return "PolynomialCalibrator"+Arrays.toString(coefficients);
	}
}
