package org.yamcs.algorithms;

import org.yamcs.ParameterValue;

/**
 * A ParameterValue as passed to an algorithm. Actual implementations are
 * generated on-the-fly to walk around the issue of Rhino that maps
 * boxed primitives to JavaScript Objects instead of Numbers
 */
public interface ValueBinding {

    public void updateValue(ParameterValue newValue); 
}
