package org.yamcs.commanding;

import java.util.Arrays;

import org.yamcs.commanding.TcParameterDefinition.SwTypes;

public class PolynomialDecalibration implements Decalibration {
	private static final long serialVersionUID = 200704191654L;
	double[] coefficients;
	/**
	 * constructs a polynomial decalibration with the given coefficients
	 * @param coefficients
	 */
	public PolynomialDecalibration(double[] coefficients){
		this.coefficients=coefficients;
	}
	
	/**
	 * @param engValue 
	 * @param rawType 
	 * @param engType 
	 * @return raw value which can be either Long or Double 
	 * @throws DecalibrationNotSupportedException 
	 * 
	 */
	public Object decalibrate(Object engValue, SwTypes rawType, SwTypes engType) throws DecalibrationNotSupportedException {
		double ev=0;
		if(engValue instanceof Long) {
			ev=(double)((Long)engValue);
		} else if (engValue instanceof Double) {
			ev=(Double) engValue;
		} else {
			throw new DecalibrationNotSupportedException("Engineering value type "+engType+" is not supported for polynomial decalibration");
		}
		double val=0;
		for(int i=coefficients.length-1;i>=0;i--) {
				val=ev*val+coefficients[i];
		}

		switch(rawType) {
		case BYTE_TYPE:
		case INTEGER_TYPE:
		case UNSIGNED_INTEGER_TYPE:
			return Long.valueOf((long) val);
		case REAL_TYPE:
			return new Double(val);
			default:
				throw new DecalibrationNotSupportedException("Raw value type "+rawType+" is not supported for polynomial decalibration");
		}
	}
	public String toString() {
		return "Polynomial"+Arrays.toString(coefficients);
	}
}

