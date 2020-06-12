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
     * Parse a value represented as string
     * 
     * @param v
     * @return
     */
    Object parseString(String v);

    /**
     * Converts a value to a string. This should be the reverse of {@link #parseString(String)}.
     * 
     * @param v
     * @return
     */
    String toString(Object v);
    
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

   
    public String getShortDescription();


    public String getLongDescription();
    
    public interface Builder<T extends Builder<T>> {
        public T setName(String name);
        public T setInitialValue(String initialValue);
        public T setShortDescription(String shortDescription);
        public T setLongDescription(String longDescription);
        public DataType build();
    }
}
