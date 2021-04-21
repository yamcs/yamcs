package org.yamcs.xtce;

import java.io.Serializable;

public class OutputParameter implements Serializable {
    private static final long serialVersionUID = 201308201307L;

    private Parameter parameter;
    private String outputName; // Optional friendly name

    public OutputParameter(Parameter parameter) {
        this.parameter = parameter;
    }
    
    public OutputParameter(Parameter parameter, String outputName) {
        this.parameter = parameter;
        this.outputName = outputName;
    }
    
    public OutputParameter() {
       super();
    }

    public Parameter getParameter() {
        return parameter;
    }
    
    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
    }
    
    public String getEffectiveOutputName() {
        return outputName == null ? parameter.getName() : outputName;
    }

    /**
     * Returns the name of the output to be used in the algorithm. This is the defined name as returned by
     * {@link #getOutputName()} or the name of the parameter if no specific name has been defined.
     */
    public String getOutputName() {
        return outputName;
    }
    
    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }
    
    @Override
    public String toString() {
        if(outputName==null) {
            return parameter.getQualifiedName();
        } else {
            return parameter.getQualifiedName()+" outputName:"+outputName;
        }
    }
}
