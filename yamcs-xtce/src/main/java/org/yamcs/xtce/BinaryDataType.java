package org.yamcs.xtce;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 3L;
    byte[] initialValue;

    /**
     * DIFFERS_FROM_XTCE XTCE does not define a size range
     */
    IntegerRange sizeRangeInBytes;

    protected BinaryDataType(Builder<?> builder) {
        super(builder);
        this.sizeRangeInBytes = builder.sizeRangeInBytes;

        if (builder.baseType instanceof BinaryDataType) {
            BinaryDataType baseType = (BinaryDataType) builder.baseType;
            if (builder.sizeRangeInBytes == null && baseType.sizeRangeInBytes != null) {
                this.sizeRangeInBytes = baseType.sizeRangeInBytes;
            }
        }
        setInitialValue(builder);
    }

    protected BinaryDataType(BinaryDataType t) {
        super(t);
        this.sizeRangeInBytes = t.sizeRangeInBytes;
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
    }

    @Override
    public byte[] getInitialValue() {
        return initialValue;
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
    public byte[] convertType(Object value) {
        if (value instanceof String) {
            return hexStringToArray((String) value);
        } else if (value instanceof byte[]) {
            return (byte[]) value;
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public String toString(Object v) {
        if (v instanceof byte[]) {
            return arrayToHexString((byte[]) v);
        } else {
            throw new IllegalArgumentException("Can only convert byte arrays");
        }
    }

    /**
     * Converts a hex string into a byte array. If the string has an odd number of hex digits, a 0 is prepended in
     * front.
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

        @Override
        public void setAncillaryData(List<AncillaryData> ancillaryData) {
            super.setAncillaryData(ancillaryData);

            long minLength = Long.MIN_VALUE;
            long maxLength = Long.MAX_VALUE;

            for (AncillaryData ad : ancillaryData) {
                if (ad.isYamcs()) {
                    SimpleEntry<String, String> p = ad.getValueAsPair();
                    if (p != null && "minLength".equals(p.getKey())) {
                        minLength = Integer.valueOf(p.getValue());
                    }
                    if (p != null && "maxLength".equals(p.getKey())) {
                        maxLength = Integer.valueOf(p.getValue());
                    }
                }
            }
            if (minLength != Long.MIN_VALUE || maxLength != Long.MAX_VALUE) {
                this.sizeRangeInBytes = new IntegerRange(minLength, maxLength);
            }
        }
    }
}
