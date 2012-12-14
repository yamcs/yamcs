package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a parameter instance to obtain the value.
 * @author nm
 *
 */
public class DynamicIntegerValue extends IntegerValue {
	private static final long serialVersionUID=200706091239L;
	transient static Logger log=LoggerFactory.getLogger(DynamicIntegerValue.class.getName());
	Parameter parameter;
	
    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
    }
    public Parameter getParameter() {
        return parameter;
    }
    
    @Override
    public String toString() {
        return "DynamicIntegerValue(parameter="+getParameter().getName()+")";
    }
}
