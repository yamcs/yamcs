package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.Parameter;

public class ValueProcessor {
    ContainerProcessingContext pcontext;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public ValueProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    /**
     * Returns the value as found from the context (or the fixed value) or null if the value could not be determined
     * 
     * @param iv
     * @return
     */
    public Long getValue(IntegerValue iv) {
        if (iv instanceof FixedIntegerValue) {
            return ((FixedIntegerValue) iv).getValue();
        } else if (iv instanceof DynamicIntegerValue) {
            return getDynamicIntegerValue((DynamicIntegerValue) iv);
        }

        throw new UnsupportedOperationException("values of type " + iv + " not implemented");
    }

    private Long getDynamicIntegerValue(DynamicIntegerValue div) {
        Parameter pref = div.getParameterInstnaceRef().getParameter();
        for (ParameterValue pv : pcontext.result.params) {
            if (pv.getParameter() == pref) {
                return (long)pv.getEngValue().getUint32Value();
            }
        }
        log.warn("Could not find the parameter in the list of extracted parameters, parameter: {}", pref);
        return null;
    }
}
