package org.yamcs.parameter;

/**
 * Union like class
 * 
 */
public abstract class Value {
    public abstract org.yamcs.protobuf.Yamcs.Value.Type getType();

    public int getUint32Value() {
        throw new UnsupportedOperationException("Cannot use getUint32Value() for " + getType() + " values");
    }

    public int getSint32Value() {
        throw new UnsupportedOperationException("Cannot use getSint32Value() for " + getType() + " values");
    }

    public long getUint64Value() {
        throw new UnsupportedOperationException("Cannot use getUint64Value() for " + getType() + " values");
    }

    public long getSint64Value() {
        throw new UnsupportedOperationException("Cannot use getSint64Value() for " + getType() + " values");
    }

    public byte[] getBinaryValue() {
        throw new UnsupportedOperationException("Cannot use getBinaryValue() for " + getType() + " values");
    }

    public String getStringValue() {
        throw new UnsupportedOperationException("Cannot use getStringValue() for " + getType() + " values");
    }

    public float getFloatValue() {
        throw new UnsupportedOperationException("Cannot use getFloatValue() for " + getType() + " values");
    }

    public double getDoubleValue() {
        throw new UnsupportedOperationException("Cannot use getDoubleValue() for " + getType() + " values");
    }

    public boolean getBooleanValue() {
        throw new UnsupportedOperationException("Cannot use getBooleanValue() for " + getType() + " values");
    }

    public long getTimestampValue() {
        throw new UnsupportedOperationException("Cannot use getTimestampValue() for " + getType() + " values");
    }

    /**
     * 
     * @return the value as signed long
     * @throws UnsupportedOperationException
     *             if the value cannot be converted - for example if a double value is encountered or an unsigned 64
     *             bits integer greater than {@link Long#MAX_VALUE}
     */
    public long toLong() {
        throw new UnsupportedOperationException("Cannot use toLong() for " + getType() + " values");
    }

    /**
     * return the value as a double. Precision will be lost when converting large integer numbers.
     * 
     * @throws UnsupportedOperationException
     *             for non numeric values.
     * 
     */
    public double toDouble() {
        throw new UnsupportedOperationException("Cannot use toLong() for " + getType() + " values");
    }
}
