package org.yamcs.xtce;

import java.util.List;

/**
 * Interface for all XTCE data types. 
 *
 * 
 * @author nm
 *
 */
public interface DataType {
    /**
     * 
     * @return the list of units
     */
    public List<UnitType> getUnitSet();
    /**
     * 
     * @return the data encoding for the data type
     */
    public DataEncoding getEncoding();
    
    /**
     * 
     * @return the name of the type
     */
    String getName();
    
    /**
     * Sets the initial value converting from string to the specific type value
     * 
     * @param initialValue
     * @throws IllegalArgumentException if the string cannot be converted
     */
    void setInitialValue(String initialValue);
}
