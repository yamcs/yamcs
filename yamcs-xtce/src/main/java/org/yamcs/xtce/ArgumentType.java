package org.yamcs.xtce;

import java.util.List;

/**
 * Interface to be implemented by all the argument types
 * 
 * @author nm
 *
 */
public interface ArgumentType extends DataType {
    /**
     * String which represents the type.
     * This string will be presented to the users of the system.
     * 
     * @return
     */
    String getTypeAsString();

    /**
     * 
     * @return the list of units
     */
    public List<UnitType> getUnitSet();

    /**
     * 
     * @return the name of the type
     */
    String getName();

    /**
     * Create a shallow copy of the data type
     * - the object itself (and the primitive fields) are new
     * but the other fields reference to the same object like the original
     * 
     * @return
     */
    <T extends ArgumentType> Builder<?> toBuilder();

    interface Builder<T extends Builder<T>> extends DataType.Builder<T> {
        T setEncoding(DataEncoding.Builder<?> dataEncoding);

        public ArgumentType build();

        DataEncoding.Builder<?> getEncoding();
    }
}
