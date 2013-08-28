package org.yamcs.xtce;

import java.io.Serializable;

public class InputParameter implements Serializable {
    private static final long serialVersionUID = 201308201312L;

    private ParameterInstanceRef parameterInstance;
    private String inputName; // Optional friendly name

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
}
