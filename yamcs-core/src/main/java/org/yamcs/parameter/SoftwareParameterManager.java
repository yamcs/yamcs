package org.yamcs.parameter;

import java.util.List;

import org.yamcs.xtce.Parameter;

/**
 * Handles parameters that can be set from the clients.
 * 
 * @author nm
 *
 */
public interface SoftwareParameterManager {

    /**
     * Called (usually via the external Yamcs API) to update a list of parameters.
     * <p>
     * Note that the value can be of type {@link PartialParameterValue} meaning that it refers to an element of an
     * array.
     * 
     */
    void updateParameters(List<ParameterValue> pvals);

    /**
     * Called (usually via the external Yamcs API) to pdate the engineering value of a parameter.
     * 
     */
    void updateParameter(Parameter p, Value v);

}
