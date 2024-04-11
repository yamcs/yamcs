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
     * Update a collection of Parameters. The parameters are provided in {@link ParameterValue} format - that means they
     * need to have associated a MDB Parameter.
     * 
     * <p>
     * The group is used as partition key in the recording (and can be used for retrieval as well).
     * 
     * <p>
     * The (gentime,group,seqNum) has to be unique for one parameter and will be used to detect and not save duplicates.
     * 
     * @param gentime
     *            - the generation time of the parameters
     * @param group
     * @param seqNum
     * @param params
     */
    public abstract void updateParameters(long gentime, String group, int seqNum, Collection<ParameterValue> params);

    /**
     * Update the parameters. Alternative method to provide ProtoBuf parameter values instead of POJO versions. The
     * parameters do not need an associated MDB Parameter but just a FullyQualifiedName.
     * <p>
     * The ParameterRecorder will use the FQN to record them. If they are sent to a processor (e.g. on a pp_realtime
     * stream), they have to be found in the MDB for clients to be able subscribe to them. Also for triggering alarms.
     * 
     * @param gentime
     * @param group
     * @param seqNum
     * @param params
     */
    void updateParams(long gentime, String group, int seqNum,
            Collection<org.yamcs.protobuf.Pvalue.ParameterValue> params);
}
