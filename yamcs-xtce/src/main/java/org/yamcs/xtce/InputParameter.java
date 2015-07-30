package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Input parameters for algorithms
 * @author nm
 *
 */
public class InputParameter implements Serializable {
    private static final long serialVersionUID = 3L;

    private ParameterInstanceRef parameterInstance;
    private String inputName; // Optional friendly name
    //if this input parameter is not set, the algorithm will not trigger
    private boolean mandatory = false;
    
    public InputParameter(ParameterInstanceRef parameterInstance) {
        this.parameterInstance = parameterInstance;
    }
    
    public InputParameter(ParameterInstanceRef parameterInstance, String inputName) {
        this.parameterInstance = parameterInstance;
        this.inputName = inputName;
    }
    
    public ParameterInstanceRef getParameterInstance() {
        return parameterInstance;
    }
    
    public void setParameterInstance(ParameterInstanceRef parameterInstance) {
        this.parameterInstance = parameterInstance;
    }
    
    public String getInputName() {
        return inputName;
    }
    
    public void setInputName(String inputName) {
        this.inputName = inputName;
    }

 
    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    public String toString() {
        if(inputName==null) return parameterInstance.toString()+(mandatory?"[M]":"");
        else return parameterInstance+" inputName:"+inputName+(mandatory?"[M]":"");
    }
}
