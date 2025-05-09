package org.yamcs.parameter;

import java.util.List;

/**
 * Used by the ParameterRequestManager to deliver parameters
 * 
 */
public interface ParameterConsumer {
    void updateItems(int subscriptionId, List<ParameterValue> items);
}
