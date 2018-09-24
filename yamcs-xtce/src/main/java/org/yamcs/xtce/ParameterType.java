package org.yamcs.xtce;

import java.util.Collections;
import java.util.Set;

/**
 * Interface implemented by all the parameters types.
 * @author nm
 *
 */
public interface ParameterType extends DataType {
    /**    
     * 
     * @return the set of parameters on which this one depends in order to be extracted or alarm checked 
     *         can be an empty set if this parameter does not depend on any other
     */
    default Set<Parameter> getDependentParameters() {
        return Collections.emptySet();
    }

    /**
     * Whether this ParameterType has any alarms associated
     */
    boolean hasAlarm();

    /**
     * parses the string into a java object according to the parameter type
     * @param stringValue
     * @return
     */
    Object parseString(String stringValue);

    /**
     * parses the string into a java object according to the parameter encoding
     * @param stringValue
     * @return
     */
    Object parseStringForRawValue(String stringValue);
    
    /**
     * set the data encoding for the parameter type
     * @param dataEncoding
     */
    void setEncoding(DataEncoding dataEncoding);

    /**
     * Create a shallow copy of the parameter type
     *  - the object itself (and the primitive fields) are new 
     *    but the other fields reference to the same object like the original 
     *  
     * @return
     */
    ParameterType copy();
}
