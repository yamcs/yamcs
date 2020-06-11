package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;

    /**
     * DIFFERS_FROM_XTCE XTCE does not define a size range
     */
    IntegerRange sizeRangeInBytes;

    protected BinaryDataType(Builder<?> builder) {
        super(builder);
        this.sizeRangeInBytes = builder.sizeRangeInBytes;
        if (builder.initialValue != null) {
            if (builder.initialValue != null) {
                this.initialValue = parseString(builder.initialValue);
            }
        }
    }

    protected BinaryDataType(BinaryDataType t) {
        super(t);
        this.sizeRangeInBytes = t.sizeRangeInBytes;
    }

    public byte[] getInitialValue() {
        return (byte[]) initialValue;
    }

    public IntegerRange getSizeRangeInBytes() {
        return sizeRangeInBytes;
    }

    public void setSizeRangeInBytes(IntegerRange sizeRangeInBytes) {
        this.sizeRangeInBytes = sizeRangeInBytes;
    }

    @Override
    public String toString() {
        return "BinaryData encoding: " + encoding;
    }

    /**
     * parse the hexadecimal stringValue into byte[]
     */
    @Override
    public byte[] parseString(String stringValue) {
        return hexStringToArray(stringValue);
    }

    @Override
    public String toString(Object v) {
        if(v instanceof byte[]) {
            return arrayToHexString((byte[]) v);
        } else {
            throw new IllegalArgumentException("Can only convert byte arrays");
        }
    }

    /**
     * Converts a hex string into a byte array.
     * If the string has an odd number of hex digits, a 0 is prepended in front.
     * 
     * if the string contains something else than 0-9, a-f, a NumberFormatException is thrown from Integer.parseInt with
     * radix 16
     * 
     * @param s
     * @return byte array resulted from parsing the string
     */
    public static byte[] hexStringToArray(String s) {
        if ((s.length() & 1) == 1) {
            s = "0" + s;
        }
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < s.length() / 2; i++) {
            b[i] = (byte) (Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16) & 0xFF);
        }
        return b;
    }

    public static String arrayToHexString(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    @Override
    public Type getValueType() {
        return Type.BINARY;
    }

    @Override
    public String getTypeAsString() {
        return "binary";
    }

    public static abstract class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        IntegerRange sizeRangeInBytes;

        public Builder() {
        }

        public Builder(BinaryDataType binaryDataType) {
            super(binaryDataType);
            this.sizeRangeInBytes = binaryDataType.sizeRangeInBytes;
        }

        public void setSizeRangeInBytes(IntegerRange sizeRangeInBytes) {
            this.sizeRangeInBytes = sizeRangeInBytes;
        }

    }

}
