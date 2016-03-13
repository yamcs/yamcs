package org.yamcs.parameter;

import java.util.Collection;

import org.yamcs.parameter.ParameterValue;

/**
 * This is the ParameterRequestManager
 * 
 * used by the provides of parameters to send parameters to PRM
 * @author nm
 *
 */
public interface ParameterRequestManager {
	/**
	 * Called each time some parameters have been updated.
	 * @param params - new delivered parameter values
	 */
	public abstract void update(Collection<ParameterValue> params);
}
