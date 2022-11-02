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
     * String which represents the type. This string will be presented to the users of the system.
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
     * Converts to the canonical (boxed) java representation of this type.
     * <p>
     * For example, if {@code value} is a {@code String}, an integer-like DataType should parse the String value, and
     * return an Integer result.
     * 
     * 
     * @param value
     *            value to be converted, use boxed primitive values.
     * @return The preferred java object representation
     * @throws IllegalArgumentException
     *             when the provided value cannot be represented by this type.
     */
    Object convertType(Object value);

    /**
     * parses the string into a java object according to the parameter encoding
     * 
     * @param stringValue
     * @return a java object representation
     * @throws IllegalArgumentException
     *             if the string cannot be parsed
     */
    Object parseStringForRawValue(String stringValue);

    /**
     * Converts a value to a string.
     * 
     * @param v
     * @return
     */
    String toString(Object v);

    /**
     * Get the initial value if any
     * 
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

    public String getQualifiedName();

    public interface Builder<T extends Builder<T>> {
        public T setName(String name);

        public T setQualifiedName(String fqn);

        public T setInitialValue(String initialValue);

        public T setShortDescription(String shortDescription);

        public T setLongDescription(String longDescription);

        public DataType build();

        public String getName();
    }
}
