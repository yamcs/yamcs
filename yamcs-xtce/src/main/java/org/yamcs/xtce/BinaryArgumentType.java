package org.yamcs.xtce;

public class BinaryArgumentType extends BinaryDataType implements ArgumentType {
    private static final long serialVersionUID = 1L;

    public BinaryArgumentType(String name) {
        super(name);
    }
    
    public BinaryArgumentType(BinaryArgumentType t1) {
        super(t1);
    }
    
    @Override
    public String getTypeAsString() {
        return "binary";
    }

    @Override
    public String toString() {
        return "BinaryArgumentType name:" + name + " encoding:" + encoding;
    }

    @Override
    public BinaryArgumentType copy() {
        return new BinaryArgumentType(this);
    }
}
