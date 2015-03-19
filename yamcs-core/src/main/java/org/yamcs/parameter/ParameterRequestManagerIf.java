package org.yamcs.parameter;

import java.util.Collection;

import org.yamcs.ParameterValue;

/**
 * This is the ParameterRequestManager
 * 
 * used by the provides of parameters to send parameters to PRM
 * @author nm
 *
 */
public interface ParameterRequestManagerIf {
	/**
	 * Called each time some parameters have been updated.
	 * @param paramDefs
	 * @param paramValues
	 */
	public abstract void update(Collection<ParameterValue> params);
}
