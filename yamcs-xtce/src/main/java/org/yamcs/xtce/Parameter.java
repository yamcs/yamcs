package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * A Parameter is a description of something that can have a value; it is not the value itself.
 */
public class Parameter extends NameDescription {
	private static final long serialVersionUID = 200706050619L;
	ParameterType parameterType;
	
	static Logger log=LoggerFactory.getLogger(Parameter.class.getName());

	public Parameter(String name) {
		super(name);
	}
	
	
	public void setParameterType(ParameterType pm) {
		parameterType = pm;
	}

	public ParameterType getParameterType() {
		return parameterType;
	}
	
	@Override
    public String toString() {
        return "ParaName: " + this.getName() + " paraType:" + parameterType +" opsname: "+getOpsName();
    }

}
