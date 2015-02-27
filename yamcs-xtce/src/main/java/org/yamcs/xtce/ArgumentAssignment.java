package org.yamcs.xtce;

public class ArgumentAssignment {
	
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
}