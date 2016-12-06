package org.yamcs.tctm;

import java.util.Collection;

import org.yamcs.parameter.ParameterValue;

/**
 * Used by the ParameterDataLink to propagate processed parameters inside Yamcs.
 * 
 *  
 * @author nm
 *
 */
public interface ParameterSink {
    /**
     * Update a collection of Parameters
     *
     */
    @Deprecated
    public abstract void updatePps(long gentime, String group, int seqNum, Collection<ParameterValue> params);

    /**
     * Update the parameters
     * @param gentime
     * @param group
     * @param seqNum
     * @param params
     */
    void updateParams(long gentime, String group, int seqNum, Collection<org.yamcs.protobuf.Pvalue.ParameterValue> params);
}

