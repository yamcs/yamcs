package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Input parameters for algorithms.
 * <p>
 * Although they are called input parameters they can also reference command arguments
 * for algorithms running part of command transmission constraints or command verifiers.
 */
public class InputParameter implements Serializable {
    private static final long serialVersionUID = 4L;

    // one of these two is null and other one not
    private final ParameterInstanceRef parameterInstance;
    private final ArgumentInstanceRef argumentRef;

    private String inputName; // Optional friendly name
    // if this input parameter is not set, the algorithm will not trigger
    private boolean mandatory = false;

    private InputParameter(ParameterInstanceRef parameterInstance, ArgumentInstanceRef argRef, String inputName) {
        this.parameterInstance = parameterInstance;
        this.argumentRef = argRef;
        this.inputName = inputName;
    }

    public InputParameter(ParameterInstanceRef parameterInstance) {
        this(parameterInstance, null, null);
    }

    public InputParameter(ParameterInstanceRef parameterInstance, String inputName) {
        this(parameterInstance, null, inputName);
    }

    public InputParameter(ArgumentInstanceRef argumentRef, String inputName) {
        this(null, argumentRef, inputName);
    }

    /**
     * @return the reference to the parameter or null if this references an argument instead
     */
    public ParameterInstanceRef getParameterInstance() {
        return parameterInstance;
    }

    /**
     * @return the reference to the command argument or null if this references a parameter instead
     */
    public ArgumentInstanceRef getArgumentRef() {
        return argumentRef;
    }

    public ParameterOrArgumentRef getRef() {
        return parameterInstance == null ? argumentRef : parameterInstance;
    }

    public String getInputName() {
        return inputName;
    }

    /**
     * Returns the name of the input to be used in the algorithm. This is the defined name as returned by
     * {@link #getInputName()} or the name of the parameter if no specific name has been defined.
     */
    public String getEffectiveInputName() {
        if (inputName != null) {
            return inputName;
        }
        if (parameterInstance != null) {
            return parameterInstance.getParameter().getName();
        }
        return argumentRef.getArgument().getName();
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
        if (inputName == null)
            return parameterInstance.toString() + (mandatory ? "[M]" : "");
        else
            return parameterInstance + " inputName:" + inputName + (mandatory ? "[M]" : "");
    }
}
