package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value;

/**
 * Interface for all XTCE data types.
 *
 * 
 * @author nm
 *
 */
public interface DataType {
    /**
     * String which represents the type.
     * This string will be presented to the users of the system.
     * 
     * @return
     */
    String getTypeAsString();

    /**
     * 
     * @return the name of the type
     */
    String getName();

    /**
     * Sets the initial value converting from string to the specific type value.
     * 
     * @param initialValue
     * @throws IllegalArgumentException
     *             if the string cannot be converted
     */
    void setInitialValue(String initialValue);


    /**
     * Parse a value represented as string
     * 
     * @param v
     * @return
     */
    Object parseString(String v);

    
    /**
     * Get the initial value if any
     * @return
     */
    Object getInitialValue();
    
    
    /**
     * Return the expected Value type of an engineering value conforming to this XTCE data type
     * 
     * @return
     */
    Value.Type getValueType();

    public void setShortDescription(String shortDescription);

    public String getShortDescription();

    public void setLongDescription(String longDescription);

    public String getLongDescription();
}
