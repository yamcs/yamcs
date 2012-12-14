package org.yamcs.commanding;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import org.yamcs.commanding.TcParameterDefinition.SwTypes;


/**
 * @author nm
 *
 * Basic interface for the tc parameter decalibration
 */
public interface Decalibration extends Serializable{
	/**
	 * Decalibrate the value: convert the engValue of type engType into a rawValue of type rawType
	 * @param engValue the value to be conv
	 * @param rawType the type of the rawValue input variable
	 * @param engType the type of the engineering value returned variable
	 * @return the 
	 * @throws DecalibrationNotSupportedException when the combination between rawType, engType and decalibration type is not supported
	 */
	public Object decalibrate(Object engValue, SwTypes rawType, SwTypes engType) throws DecalibrationNotSupportedException;
}

class DecalibrationNotSupportedException extends Exception {

	public DecalibrationNotSupportedException(String reason) {
		super(reason);
	}
	
}

class IdenticaDecalibration implements Decalibration {
	private static final long serialVersionUID = 200704191654L;
	/**
	 * @param engValue 
	 * @param rawType ignored
	 * @param engType ignored
	 * @return engValue 
	 */
	public Object decalibrate(Object engValue, SwTypes rawType, SwTypes engType) {
		return engValue;
	}
	
	public String toString() {
		return "Identical";
	}
}
