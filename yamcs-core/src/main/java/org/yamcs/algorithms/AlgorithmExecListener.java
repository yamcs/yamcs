package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;

public interface AlgorithmExecListener {
    /**
     * Called when the algorithm has run successfully
     * 
     * @param inputValues
     *            - may be null if the algorithm does not have any input values or does not keep track of them
     * @param returnValue
     *            - may be null if the algorithm does not return anything
     * @param outputValues
     *            - may be empty if there is no output value
     */
    public void algorithmRun(List<RawEngValue> inputValues, Object returnValue, List<ParameterValue> outputValues);

    /**
     * Called when the algorithm produced an error
     * 
     * @param inputValues
     * @param errorMsg
     */
    public default void algorithmError(List<RawEngValue> inputValues, String errorMsg) {

    }

}
