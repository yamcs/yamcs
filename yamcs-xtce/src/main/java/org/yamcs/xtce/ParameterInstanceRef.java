package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A reference to an instance of a Parameter.
 * Used when the value of a parameter is required for a calculation or as an index value.  
 * A positive value for instance is forward in time, a negative value for count is backward in time, 
 * a 0 value for count means use the current value of the parameter or the first value in a container.
 * @author nm
 *
 */
public class ParameterInstanceRef implements Serializable {
    private static final long serialVersionUID = 200906191236L;
    private Parameter parameter;

    private boolean useCalibratedValue=true;
    private int instance=0;

    public ParameterInstanceRef(Parameter para) {
        this.parameter=para;
    }

    public ParameterInstanceRef(Parameter para, boolean useCalibratedValue) {
        this.parameter=para;
        this.useCalibratedValue=useCalibratedValue;
    }

    public ParameterInstanceRef(boolean useCalibratedValue) {
        this.useCalibratedValue=useCalibratedValue;
    }

    public void setParameter(Parameter para) {
        this.parameter=para;
    }	

    public Parameter getParameter() {
        return parameter;
    }

    public boolean useCalibratedValue() {
        return useCalibratedValue;
    }

    public void setUseCalibratedValue(boolean useCalibratedValue) {
        this.useCalibratedValue=useCalibratedValue;
    }

    public void setInstance(int instance) {
        this.instance = instance;
    }

    public int getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return parameter.getQualifiedName()+" instance:"+instance;
    }
}
