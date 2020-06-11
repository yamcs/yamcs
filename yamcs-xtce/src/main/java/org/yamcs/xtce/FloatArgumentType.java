package org.yamcs.xtce;

/**
 * Represent aspects of an float, probably using IntegerDataEncoding with a calibrator or FloatDataEncoding.
 * 
 * @author nm
 *
 */
public class FloatArgumentType extends FloatDataType implements ArgumentType {
    private static final long serialVersionUID=1L;

    /**
     * Creates a shallow copy.
     */
    public FloatArgumentType(FloatArgumentType t) {
        super(t);
    }

    public FloatArgumentType(Builder builder) {
       super(builder);
    }
    
    public String getTypeAsString() {
        return "float";
    }

    @Override
    public String toString() {
        return "FloatParameterType name:"+name+" sizeInBits:"+sizeInBits+" encoding:"+encoding;
    }
    
    @Override
    public FloatArgumentType copy() {
        return new FloatArgumentType(this);
    }
    
    public static class Builder extends FloatDataType.Builder<Builder> {

        @Override
        public FloatArgumentType build() {
            return new FloatArgumentType(this);
        }
        
    }
}
