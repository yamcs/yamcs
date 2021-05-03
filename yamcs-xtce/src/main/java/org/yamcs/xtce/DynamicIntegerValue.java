package org.yamcs.xtce;

/**
 * Uses a parameter instance to obtain the value.
 * <p>
 * Note that this explicitly supports only integer values (whereas XTCE supports also doubles)
 * 
 */
public class DynamicIntegerValue extends IntegerValue {
    private static final long serialVersionUID = 14L;
    private long intercept = 0;
    private long slope = 1;
    private ParameterOrArgumentRef instanceRef;

    public DynamicIntegerValue(ParameterOrArgumentRef ir) {
        this.instanceRef = ir;
    }

    public ParameterInstanceRef getParameterInstanceRef() {
        if (!(instanceRef instanceof ParameterInstanceRef)) {
            throw new IllegalStateException(
                    "In DynamicIntegerValue: wanted ParameterInstanceRef but got "
                            + instanceRef.getClass().getName());
        }
        return (ParameterInstanceRef) instanceRef;
    }

    public ParameterOrArgumentRef getDynamicInstanceRef() {
        return instanceRef;
    }

    public long getIntercept() {
        return intercept;
    }

    public void setIntercept(long intercept) {
        this.intercept = intercept;
    }

    public long getSlope() {
        return slope;
    }

    public void setSlope(long slope) {
        this.slope = slope;
    }

    /**
     * Transform the value with the intercept and slope.
     * 
     * @throws ArithmeticException
     *             if the result overflows a long
     */
    public long transform(long v) {
        return Math.addExact(Math.multiplyExact(v, slope), intercept);
    }

    /**
     * Reverse operation for {@link #transform(long)}
     * 
     * @throws ArithmeticException
     *             if the result overflows a long
     */
    public long reverse(long v) {
        return Math.subtractExact(v / slope, intercept);
    }

    @Override
    public String toString() {
        return "DynamicIntegerValue(instanceRef=" + instanceRef.getName()
                + ", slope=" + slope + ", intercept=" + intercept + ")";
    }
}
