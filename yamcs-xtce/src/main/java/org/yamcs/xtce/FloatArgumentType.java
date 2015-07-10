package org.yamcs.xtce;

/**
 * Represent aspects of an float, probably using IntegerDataEncoding with a calibrator or FloatDataEncoding.
 * 
 * @author nm
 *
 */
public class FloatArgumentType extends FloatDataType implements ArgumentType {
    private static final long serialVersionUID=1L;

    public FloatArgumentType(String name){
        super(name);
    }


    public String getTypeAsString() {
        return "float";
    }

    @Override
    public String toString() {
        return "FloatParameterType name:"+name+" sizeInBits:"+sizeInBits+" encoding:"+encoding;
    }
}
