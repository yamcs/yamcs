package org.yamcs.parameter;

import java.util.Collection;
import java.util.List;

import org.yamcs.xtce.Parameter;

public interface ParameterCache {

    /**
     * update the parameters in the cache
    
     * @param pvs - parameter value list
     */
    void update(Collection<ParameterValue> pvs);

    /**
     * Returns cached value for parameter or an empty list if there is no value in the cache
     * 
     * 
     * @param plist
     * @return
     */
    List<ParameterValue> getValues(List<Parameter> plist);

    /**
     * Returns last cached value for parameter or null if there is no value in the cache
     * @param p - parameter for which the last value is returned
     * @return
     */
    ParameterValue getLastValue(Parameter p);

    /**
     * Returns all values from the cache for the parameter or null if there is no value cached
     * 
     * The parameter are returned in descending order (newest parameter is returned first)
     * @param p - parameter for which all values are returned
     * @return
     */
    List<ParameterValue> getAllValues(Parameter p);

}
