package org.yamcs;

import java.util.ArrayList;

/**
 * Used by the ParameterRequestManager to deliver parameters
 * 
 * @author nm
 *
 */
public interface ParameterConsumer {

	void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items);

}
