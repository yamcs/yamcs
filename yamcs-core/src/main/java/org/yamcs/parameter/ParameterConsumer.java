package org.yamcs.parameter;

import java.util.ArrayList;

import org.yamcs.ParameterValue;

/**
 * Used by the ParameterRequestManager to deliver parameters
 * 
 * @author nm
 *
 */
public interface ParameterConsumer {

	void updateItems(int subscriptionId, ArrayList<ParameterValue> items);

}
