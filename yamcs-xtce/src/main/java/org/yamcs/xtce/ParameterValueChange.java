package org.yamcs.xtce;

import java.io.Serializable;

/**
 * XTCE: A parameter change in value or specified delta change in value
 *
 */
public class ParameterValueChange implements Serializable {
    private static final long serialVersionUID = 1L;

    double delta;

    // reference to a parameter (parameterRef.instance is always 0)
    ParameterInstanceRef parameterRef;


    public void setParameterRef(ParameterInstanceRef parameterRef) {
        this.parameterRef = parameterRef;
    }

    public ParameterInstanceRef getParameterRef() {
        return parameterRef;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }
    public double getDelta() {
        return delta;
    }
}
