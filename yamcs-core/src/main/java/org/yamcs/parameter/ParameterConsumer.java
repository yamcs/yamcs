package org.yamcs.parameter;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

/**
 * Used by the ParameterRequestManager to deliver parameters
 * 
 * @author nm
 *
 */
public interface ParameterConsumer {
    void updateItems(int subscriptionId, List<ParameterValue> items);
}
