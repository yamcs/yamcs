package org.yamcs.xtce;

public class StringArgumentType extends StringDataType implements ArgumentType {
    private static final long serialVersionUID = 1L;

    public StringArgumentType(String name) {
        super(name);
    }

    @Override
    public String getTypeAsString() {
        return "string";
    }
    

    @Override
    public String toString() {
        return "StringArgumentType name:"+name+" encoding:"+encoding;
    }

}
