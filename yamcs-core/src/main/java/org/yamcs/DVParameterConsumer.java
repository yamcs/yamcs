package org.yamcs;

import java.util.ArrayList;

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
    public ArrayList<ParameterValue> updateParameters(int subcriptionid, ArrayList<ParameterValue> items);
}
