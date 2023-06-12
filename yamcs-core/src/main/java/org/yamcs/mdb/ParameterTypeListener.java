package org.yamcs.mdb;

import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;

public interface ParameterTypeListener {

    /**
     * Called when a parameter's type is updated.
     */
    void parameterTypeUpdated(Parameter parameter, ParameterType ptype);
}
