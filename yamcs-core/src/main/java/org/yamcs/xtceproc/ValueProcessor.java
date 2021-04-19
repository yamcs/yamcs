package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.ParameterInstanceRef;

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
        ParameterInstanceRef pref = div.getParameterInstanceRef();
        ParameterValue pv = pcontext.result.getTmParameterInstance(pref.getParameter(), pref.getInstance(), false);
        if (pv == null) {
            log.warn("Could not find a value for the parameter instance: {}",
                    div.getParameterInstanceRef());
            return null;
        } else {
            return (long) pv.getEngValue().getUint32Value();
        }
    }
}
