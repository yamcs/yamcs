package org.yamcs.xtce;

/**
 * A simple long value
 * @author nm
 *
 */
public class FixedIntegerValue extends IntegerValue {
    private static final long serialVersionUID=200706091239L;
    long value;
    
    public FixedIntegerValue(long value) {
        this.value = value;
    }
    
    
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "FixedIntegerValue("+value+")";
    }
}
