package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a parameter instance to obtain the value.
 * @author nm
 *
 */
public class DynamicIntegerValue extends IntegerValue {
    private static final long serialVersionUID=201603101239L;
    
    transient static Logger log=LoggerFactory.getLogger(DynamicIntegerValue.class.getName());
    ParameterInstanceRef instanceRef;;


    public void setParameterInstanceRef(ParameterInstanceRef pir) {
        this.instanceRef = pir;
    }

    public ParameterInstanceRef getParameterInstnaceRef() {
        return instanceRef;
    }
    
    @Override
    public String toString() {
        return "DynamicIntegerValue(parameterInstance="+instanceRef.getParameter().getName()+")";
    }

}
