package org.yamcs.xtce;

import java.io.Serializable;

public class ArgumentAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    final String argumentName;
    final String argumentValue;

    public ArgumentAssignment(String argumentName, String argumentValue) {
        if (argumentName == null) {
            throw new NullPointerException("argumentName cannot be null");
        }
        if (argumentValue == null) {
            throw new NullPointerException("argumentValue cannot be null");
        }
        this.argumentName = argumentName;
        this.argumentValue = argumentValue;
    }

    public String getArgumentName() {
        return argumentName;
    }

    public String getArgumentValue() {
        return argumentValue;
    }

    @Override
    public String toString() {
        return argumentName + "=" + argumentValue;
    }
}
