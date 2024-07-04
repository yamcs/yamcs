package org.yamcs.parameter;

import java.util.Arrays;
import java.util.List;

import org.yamcs.mdb.DataTypeProcessor;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;

/**
 * Handles parameters that can be set from the clients.
 * 
 */
public interface SoftwareParameterManager {

    /**
     * Called (usually via the external Yamcs API) to update a list of parameters.
     * <p>
     * Note that the value can be of type {@link PartialParameterValue} meaning that it refers to an element of an
     * array.
     * 
     */
    void updateParameters(List<ParameterValue> pvals);

    /**
     * Called (usually via the external Yamcs API) to pdate the engineering value of a parameter.
     * 
     */
    default void updateParameter(Parameter p, Value engValue) {
        ParameterValue pv = new ParameterValue(p);
        pv.setEngValue(engValue);

        List<ParameterValue> pvlist = Arrays.asList(pv);
        updateParameters(pvlist);
    }

    /**
     * Transforms a parameter value into its target type
     * <p>
     * Also assembles a full aggregate value out of a number of partial members, taking the rest from the current value
     * in the cache
     * <p>
     * Throws an {@link IllegalArgumentException} if it receives a partial aggregate value and does not find a full
     * value in the cache
     */
    public static ParameterValue transformValue(LastValueCache lvc, ParameterValue pv) {
        Parameter p = pv.getParameter();
        ParameterType ptype = p.getParameterType();
        if (ptype == null) {
            return pv;
        }

        ParameterValue r;

        if (pv instanceof PartialParameterValue) {
            ParameterValue oldValue = lvc.getValue(p);
            if (oldValue == null) {
                throw new IllegalArgumentException("Received request to partially update " + p.getQualifiedName()
                        + " but has no value in the cache");
            }
            r = new ParameterValue(oldValue);
            AggregateUtil.updateMember(r, (PartialParameterValue) pv);
        } else {
            Value v = DataTypeProcessor.convertEngValueForType(ptype, pv.getEngValue());
            r = new ParameterValue(pv);
            r.setEngValue(v);
        }

        return r;
    }

}
