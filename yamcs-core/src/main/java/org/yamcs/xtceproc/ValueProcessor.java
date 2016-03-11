package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.Parameter;

public class ValueProcessor {
    ProcessingContext pcontext;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    public ValueProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    public long getValue(IntegerValue iv) {
        if(iv instanceof FixedIntegerValue) {
            return ((FixedIntegerValue) iv).getValue();
        } else if(iv instanceof DynamicIntegerValue) {
            return getDynamicIntegerValue((DynamicIntegerValue)iv);
        }

        throw new UnsupportedOperationException("values of type "+iv+" not implemented");
    }

    private long getDynamicIntegerValue(DynamicIntegerValue div) {
        Parameter pref = div.getParameterInstnaceRef().getParameter();
        for(ParameterValue pv:pcontext.paramResult) {
            if(pv.getParameter()==pref) {
                return pv.getEngValue().getUint32Value();
            }
        }
        log.warn("Could not find the parameter in the list of extracted parameters, parameter:" + pref);
        return 0;
    }

}
