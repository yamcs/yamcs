package org.yamcs.xtce;

/**
 * Describe the name of an Argument its NameReference to an ArgumentType in ArgumentTypeSet
 * @author nm
 *
 */
public class Argument extends NameDescription {
    private static final long serialVersionUID = 6L;

    public Argument(String name) {
        super(name);
    }

    ArgumentType argumentType;

    /*
     * Used to set the initial calibrated values of Arguments.
     * Will overwrite an initial value defined for the ArgumentType.  
     *  
     *  For integer types base 10 (decimal) form is assumed unless: 
     *    if proceeded by a 0b or 0B, value is in base two (binary form, if proceeded by a 0o or 0O, values is in base 8 (octal) form, or if proceeded by a 0x or 0X, value is in base 16 (hex) form.  
     *    
     *    Floating point types may be specified in normal (100.0) or scientific (1.0e2) form.  Time types are specified using the ISO 8601 formats described for XTCE time data types. 
     *    Initial values for string types, may include C language style (\n, \t, \", \\, etc.) escape sequences.  Initial values for Array or Aggregate types may not be set.
     *    
     *    THIS is used as a default value when sending a command that does not have specified the value for this argument
     */
    String initialValue;

    public ArgumentType getArgumentType () {
        return argumentType;
    }

    public void setArgumentType(ArgumentType argumentType) {
        this.argumentType = argumentType;
    }

    /**
     * returns the initial value of this argument which would be better called default value
     * 
     * returns null if no initial value has been set
     * @return initial value or null if no initial value has been set
     */
    public String getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(String v) {
        this.initialValue = v;
    }

    @Override
    public String toString() {
        return "ArgName: " + this.getName() + ((initialValue==null)?"":" initialValue: "+initialValue)+ " argType:" + argumentType;
    }

}
