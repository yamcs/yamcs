package org.yamcs.xtce;

/**
 * Describe the name of an Argument its NameReference to an ArgumentType in ArgumentTypeSet
 *
 * @author nm
 *
 */
public class Argument extends NameDescription {
    private static final long serialVersionUID = 7L;

    public Argument(String name) {
        super(name);
    }

    ArgumentType argumentType;

    /*
     *    THIS is used as a default value when sending a command that does not have specified the value for this argument
     */
    Object initialValue;

    public ArgumentType getArgumentType() {
        return argumentType;
    }

    public void setArgumentType(ArgumentType argumentType) {
        this.argumentType = argumentType;
    }

    /**
     * returns the initial value of this argument which would be better called default value
     * 
     * returns null if no initial value has been set
     *
     * @return initial value or null if no initial value has been set
     */
    public Object getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Object v) {
        this.initialValue = v;
    }

    @Override
    public String toString() {
        return "ArgName: " + this.getName() + ((initialValue == null) ? "" : " initialValue: " + initialValue)
                + " argType:" + argumentType;
    }

}
