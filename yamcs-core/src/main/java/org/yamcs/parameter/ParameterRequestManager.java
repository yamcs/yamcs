package org.yamcs.parameter;

import java.util.Collection;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.ParameterValue;

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
	 * @param paramDefs
	 * @param paramValues
	 */
	void update(List<ContainerExtractionResult> containers, Collection<ParameterValue> params);
	void update(Collection<ParameterValue> params);
}
