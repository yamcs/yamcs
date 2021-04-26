package org.yamcs.xtceproc;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.ParameterInstanceRef;

public class ValueProcessor {
    ContainerProcessingContext pcontext;

    public ValueProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    /**
     * Returns the value as found from the context (or the fixed value) or null if the value could not be determined
     * 
     * @param iv
     * @return
     */
    public long getValue(IntegerValue iv) {
        if (iv instanceof FixedIntegerValue) {
            return ((FixedIntegerValue) iv).getValue();
        } else if (iv instanceof DynamicIntegerValue) {
            return getDynamicIntegerValue((DynamicIntegerValue) iv);
        }

        throw new UnsupportedOperationException("values of type " + iv + " not implemented");
    }

    private long getDynamicIntegerValue(DynamicIntegerValue div) throws XtceProcessingException {
        ParameterInstanceRef pref = div.getParameterInstanceRef();
        ParameterValue pv = pcontext.result.getTmParameterInstance(pref.getParameter(), pref.getInstance(), false);
        if (pv == null) {
            throw new XtceProcessingException(
                    "Could not find a value for the parameter instance: " + div.getParameterInstanceRef());
        } else {
            Value v = pref.useCalibratedValue() ? pv.getEngValue() : pv.getRawValue();
            if (v == null) {
                throw new XtceProcessingException(
                        "Could not find a " + (pref.useCalibratedValue() ? "calibrated" : "raw")
                                + " value for the parameter instance: " + div.getParameterInstanceRef());
            }
            try {
                return Math.addExact(v.toLong(), div.getIntercept());
            } catch (UnsupportedOperationException | ArithmeticException e) {
                throw new XtceProcessingException(
                        "Could extract dynamic integer value from " + div.getParameterInstanceRef() + "(" + v + ") : "
                                + e.getMessage());

            }
        }
    }
}
