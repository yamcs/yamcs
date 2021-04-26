package org.yamcs.xtce;

/**
 * Uses a parameter instance to obtain the value.
 * <p>
 * Note that this explicitly supports only integer values (whereas XTCE supports also doubles)
 * <p>
 * there is support for intercept but not for slope;
 * 
 */
public class DynamicIntegerValue extends IntegerValue {
    private static final long serialVersionUID = 201603101239L;
    private long intercept;
    ParameterOrArgumentRef instanceRef;

    public DynamicIntegerValue() {
    }

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

    @Override
    public String toString() {
        return "DynamicIntegerValue(instanceRef=" + instanceRef.getName()
                + ", intercept=" + intercept + ")";
    }
}
