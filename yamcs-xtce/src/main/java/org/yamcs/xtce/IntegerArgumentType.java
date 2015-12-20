package org.yamcs.xtce;


public class IntegerArgumentType extends IntegerDataType implements ArgumentType {
    private static final long serialVersionUID=3L;

    public IntegerArgumentType(String name){
        super(name);
    }

    @Override
    public String getTypeAsString() {
        return "integer";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IntegerArgumentType name:").append(name)
        .append(" sizeInBits:").append(sizeInBits)
        .append(" signed: ").append(signed);

        if(initialValue!=null) sb.append(", defaultValue: ").append(initialValue);
        if(validRange!=null) sb.append(", validRange: ").append(validRange.toString(isSigned()));

        sb.append(", encoding: ").append(encoding);

        return sb.toString();
    }
}
