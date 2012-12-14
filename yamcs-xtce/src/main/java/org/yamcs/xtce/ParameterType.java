package org.yamcs.xtce;

import java.util.Set;

/**
 * Interface implemented by all the parameters types.
 * @author nm
 *
 */
public interface ParameterType {
	/**
	 * 
	 * @return the set of parameters on which this one depends in order to be extracted or alarm checked 
	 */
	Set<Parameter> getDependentParameters();
	
	/**
	 * String which represents the type.
	 * This string will be presented to the users of the system.
	 * @return
	 */
	String getTypeAsString();
}
