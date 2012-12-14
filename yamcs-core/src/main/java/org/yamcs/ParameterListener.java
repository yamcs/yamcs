package org.yamcs;

import java.util.Collection;

/**
 * This interface has been created in order to be able to test the ParameterProvider classes without
 * the need for the full blown ParameterRequestManager
 * @author nm
 *
 */
public interface ParameterListener {
	/**
	 * Called each time some parameters have been updated.
	 * @param paramDefs
	 * @param paramValues
	 */
	public abstract void update(Collection<ParameterValue> params);
}
