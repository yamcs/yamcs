package org.yamcs.xtce;

import java.io.Serializable;

public class ArgumentAssignment implements Serializable{
    private static final long serialVersionUID = 1L;


    final String argumentName;
    final String argumentValue;


    public ArgumentAssignment(String argumentName, String argumentValue) {
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
        return argumentName+"="+argumentValue;
    }
}
