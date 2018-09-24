package org.yamcs.xtce;

/**
 * Describe a member field in an AggregateDataType. Each member has a name and a type reference to a data type for the
 * aggregate member name. If this aggregate is a Parameter aggregate, then the typeRef is a parameter type reference. If
 * this aggregate is an Argument aggregate, then the typeRef is an argument type reference. References to an array data
 * type is currently not supported. Circular references are not allowed.
 * 
 * @author nm
 *
 */
public class Member extends NameDescription {
    private static final long serialVersionUID = 1L;

    String initialValue;
    DataType type;

    public Member(String name) {
        super(name);
    }

    /**
     * Used to set the initial calibrated values of Parameters.
     * 
     * @param initialValue
     *            - initial calibrated value
     */
    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }

    public void setDataType(DataType dtype) {
        this.type = dtype;
    }

    public DataType getType() {
        return type;
    }
}
