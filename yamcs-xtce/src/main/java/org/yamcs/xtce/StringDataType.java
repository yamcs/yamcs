package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class StringDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;
    String initialValue;

    /**
     * For telemetry, specify as UTF-8 or UTF-16 value to match the encoding. This range check will be applied before
     * conversion to the host string data type. For commanding, the range check occurs on the string host data type
     * encoding, whatever that is -- before injection on the command link.
     */
    IntegerRange sizeRangeInCharacters;

    protected StringDataType(Builder<?> builder) {
        super(builder);
        this.sizeRangeInCharacters = builder.sizeRangeInCharacters;

        if (builder.baseType instanceof StringDataType) {
            StringDataType baseType = (StringDataType) builder.baseType;
            if (builder.sizeRangeInCharacters == null && baseType.sizeRangeInCharacters != null) {
                this.sizeRangeInCharacters = baseType.sizeRangeInCharacters;
            }
        }

        setInitialValue(builder);
    }

    protected StringDataType(StringDataType t) {
        super(t);
        this.sizeRangeInCharacters = t.sizeRangeInCharacters;
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
    }

    @Override
    public String getInitialValue() {
        return (String) initialValue;
    }

    public IntegerRange getSizeRangeInCharacters() {
        return sizeRangeInCharacters;
    }

    @Override
    public String convertType(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public Type getValueType() {
        return Type.STRING;
    }

    @Override
    public String getTypeAsString() {
        return "string";
    }

    public static abstract class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        IntegerRange sizeRangeInCharacters;

        public Builder() {
        }

        public Builder(StringDataType stringType) {
            super(stringType);
            this.sizeRangeInCharacters = stringType.sizeRangeInCharacters;
        }

        public void setSizeRangeInCharacters(IntegerRange sizeRangeInCharacters) {
            this.sizeRangeInCharacters = sizeRangeInCharacters;
        }
    }
}
