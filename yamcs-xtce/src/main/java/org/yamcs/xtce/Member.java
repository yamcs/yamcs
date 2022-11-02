package org.yamcs.xtce;

/**
 * Describe a member field in an AggregateDataType.
 * <p>
 * Each member has a name and a type reference to a data type for the aggregate member name.
 * <p>
 * If this aggregate is a Parameter aggregate, then the typeRef is a parameter type reference.
 * <p>
 * If this aggregate is an Argument aggregate, then the typeRef is an argument type reference.
 * <p>
 * References to an array data type is currently not supported. Circular references are not allowed.
 * 
 * @author nm
 *
 */
public class Member extends NameDescription {
    private static final long serialVersionUID = 1L;

    Object initialValue;
    DataType type;

    public Member(String name) {
        super(name);
    }

    public Member(String name, DataType type) {
        super(name);
        this.type = type;
    }

    /**
     * Used to set the initial calibrated values of Parameters. Will overwrite an initial value defined for the DataType
     * 
     * @param initialValue
     *            - initial calibrated value
     */
    public void setInitialValue(Object initialValue) {
        this.initialValue = type.convertType(initialValue);
    }

    public void setDataType(DataType dtype) {
        this.type = dtype;
    }

    public DataType getType() {
        return type;
    }

    /**
     * Get the initial value of the member.
     *
     * @return
     */
    public Object getInitialValue() {
        return initialValue;
    }
}
