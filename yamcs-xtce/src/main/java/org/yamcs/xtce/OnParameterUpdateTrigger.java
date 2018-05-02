package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Names a parameter that upon change will start the execution of the algorithm.
 * Holds a parameter reference name for a parameter that when it changes, will
 * cause this algorithm to be executed.
 */
public class OnParameterUpdateTrigger implements Serializable {
    private static final long serialVersionUID = 2L;

    private Parameter parameter;
    
    public OnParameterUpdateTrigger(Parameter parameter) {
        this.parameter = parameter;
    }
    
    //constructor to be used when the parameter has to be resolved later
    public OnParameterUpdateTrigger() {
    }
    
    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter getParameter() {
        return parameter;
    }
    
    @Override
    public String toString() {
        return parameter.getQualifiedName();
    }
}
