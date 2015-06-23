package org.yamcs.xtce;

import java.util.List;
import java.util.Set;

/**
 * Interface implemented by all the parameters types.
 * @author nm
 *
 */
public interface ParameterType {
    /**
     * 
     * @return the set of parameters on which this one depends in order to be extracted or alarm checked 
     */
    Set<Parameter> getDependentParameters();

    /**
     * String which represents the type.
     * This string will be presented to the users of the system.
     * @return
     */
    String getTypeAsString();

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
     * 
     * @return the list of units
     */
    public List<UnitType> getUnitSet();
    
    /**
     * 
     * @return the data encoding for the parameter
     */
    public DataEncoding getEncoding();
}
