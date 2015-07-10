package org.yamcs.xtce;

public class BooleanDataType extends BaseDataType {
    private static final long serialVersionUID = 4423703822819238835L;
    
    /**
     * Used mainly for command arguments to specify the default value
     */
    Boolean initialValue;
    

    BooleanDataType(String name) {
        super(name);
    }

    @Override
    public String toString(){ 
        return "BooleanData encoding: " + encoding;
    }
    
    
    /**
     * Returns {@link Boolean.parseBoolean(stringValue)}
     */
    public Object parseString(String stringValue) {
        return Boolean.parseBoolean(stringValue);
    }
}
