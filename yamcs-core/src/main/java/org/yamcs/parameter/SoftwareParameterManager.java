package org.yamcs.parameter;

import java.util.List;

import org.yamcs.xtce.Parameter;

/**
 * Handles parameters that can be set from the clients.
 * 
 * @author nm
 *
 *///this should be renamed to SoftwareParameterManager when we remove that class kept for backward compatibility
public interface SoftwareParameterManager {

    /**
     * Update a list of parameters.
     * Note that the value can be of type {@link PartialParameterValue} meaning that it refers to an element of an array. 
     * 
     * @param pvals
     */
    void updateParameters(List<ParameterValue> pvals);

    /**
     * Update the engineering value of a parameter.
     * @param p
     * @param v
     */
    void updateParameter(Parameter p, Value v);

}
