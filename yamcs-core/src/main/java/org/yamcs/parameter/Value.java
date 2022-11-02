package org.yamcs.parameter;

/**
 * Union like class
 * 
 */
public abstract class Value {
    public abstract org.yamcs.protobuf.Yamcs.Value.Type getType();

    public int getUint32Value() {
        throw cantUseException("getUint32Value()");
    }

    public int getSint32Value() {
        throw cantUseException("getSint32Value()");
    }

    public long getUint64Value() {
        throw cantUseException("getUint64Value()");
    }

    public long getSint64Value() {
        throw cantUseException("getSint64Value()");
    }

    public byte[] getBinaryValue() {
        throw cantUseException("getBinaryValue()");
    }

    public String getStringValue() {
        throw cantUseException("getStringValue()");
    }

    public float getFloatValue() {
        throw cantUseException("getFloatValue()");
    }

    public double getDoubleValue() {
        throw cantUseException("getDoubleValue()");
    }

    public boolean getBooleanValue() {
        throw cantUseException("getBooleanValue()");
    }

    public long getTimestampValue() {
        throw cantUseException("getTimestampValue()");
    }

    /**
     * 
     * @return the value as signed long
     * @throws UnsupportedOperationException
     *             if the value cannot be converted - for example if a double value is encountered or an unsigned 64
     *             bits integer greater than {@link Long#MAX_VALUE}
     */
    public long toLong() {
        throw cantUseException("toLong()");
    }

    /**
     * return the value as a double. Precision will be lost when converting large integer numbers.
     * 
     * @throws UnsupportedOperationException
     *             for non numeric values.
     * 
     */
    public double toDouble() {
        throw cantUseException("toDouble()");
    }

    private UnsupportedOperationException cantUseException(String method) {
        return new UnsupportedOperationException("Cannot use " + method + " for " + getType() + " values");
    }
}
