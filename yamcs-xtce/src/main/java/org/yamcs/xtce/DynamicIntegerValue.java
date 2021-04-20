package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a parameter instance to obtain the value.
 * 
 * @author nm
 *
 */
public class DynamicIntegerValue extends IntegerValue {
    private static final long serialVersionUID = 201603101239L;

    transient static Logger log = LoggerFactory.getLogger(DynamicIntegerValue.class.getName());
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

    @Override
    public String toString() {
        return "DynamicIntegerValue(instanceRef=" + instanceRef.getName()
                + ")";
    }

}
