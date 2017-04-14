package org.yamcs;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

/**
 * Used by the ParameterRequestManager to deliver parameters.
 * 
 * Almost the same as ParameterConsumer but this can provide itself some parameters in return. 
 * 
 * 
 * @author nm
 *
 */
public interface DVParameterConsumer {
    public List<ParameterValue> updateParameters(int subcriptionid, List<ParameterValue> items);
}
