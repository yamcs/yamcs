package org.yamcs.parameter;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.utils.TimeEncoding;

/**
 * Abstract class storing a raw value, engineering value and a generation time.
 * <p>
 * It used as base class by {@link ParameterValue} and {@link ArgumentValue}
 *
 */
public abstract class RawEngValue {
    protected Value rawValue;
    protected Value engValue;
    protected long generationTime = TimeEncoding.INVALID_INSTANT;

    public RawEngValue() {
    }

    // copy constructor - copies all the fields in a shallow mode
    public RawEngValue(RawEngValue pv) {
        this.rawValue = pv.rawValue;
        this.engValue = pv.engValue;
        this.generationTime = pv.generationTime;
    }

    public Value getEngValue() {
        return engValue;
    }

    public Value getRawValue() {
        return rawValue;
    }

    public long getGenerationTime() {
        return generationTime;
    }

    public void setRawValue(Value rv) {
        this.rawValue = rv;
    }

    public void setGenerationTime(long instant) {
        generationTime = instant;
    }

    public void setRawValue(byte[] b) {
        rawValue = new BinaryValue(b);
    }

    public void setRawFloatValue(float f) {
        rawValue = new FloatValue(f);
    }

    public void setRawDoubleValue(double d) {
        rawValue = new DoubleValue(d);
    }

    public void setRawValue(boolean b) {
        rawValue = new BooleanValue(b);
    }

    public void setRawValue(String s) {
        rawValue = new StringValue(s);
    }

    public void setRawSignedInteger(int x) {
        rawValue = new SInt32Value(x);
    }

    public void setRawUnsignedInteger(int x) {
        rawValue = new UInt32Value(x);
    }

    public void setRawSignedLong(long x) {
        rawValue = new SInt64Value(x);
    }

    public void setRawUnsignedLong(long x) {
        rawValue = new UInt64Value(x);
    }

    public void setStringValue(String s) {
        engValue = new StringValue(s);
    }

    public void setBinaryValue(byte[] v) {
        engValue = new BinaryValue(v);
    }

    public void setBooleanValue(boolean b) {
        engValue = new BooleanValue(b);
    }

    public void setDoubleValue(double v) {
        engValue = new DoubleValue(v);
    }

    public void setFloatValue(float v) {
        engValue = new FloatValue(v);
    }

    public void setSignedIntegerValue(int v) {
        engValue = new SInt32Value(v);
    }

    public void setUnsignedIntegerValue(int v) {
        engValue = new UInt32Value(v);
    }

    public void setSignedLongValue(long v) {
        engValue = new SInt64Value(v);
    }

    public void setUnsignedLongValue(long v) {
        engValue = new UInt64Value(v);
    }

    public void setEngValue(Value ev) {
        this.engValue = ev;
    }

    @Deprecated
    /**
     * 
     * @deprecated use {@link #setEngValue(Value)} (for consistency with the getter)
     */
    public void setEngineeringValue(Value ev) {
        this.engValue = ev;
    }

    public boolean hasGenerationTime() {
        return generationTime != TimeEncoding.INVALID_INSTANT;
    }
}
