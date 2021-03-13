package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

public interface AlgorithmExecListener {
    /**
     * Called when the algorithm has run successfully
     * 
     * @param inputValues
     * @param returnValue
     * @param outputValues
     */
    public void algorithmRun(List<ParameterValue> inputValues, Object returnValue, List<ParameterValue> outputValues);

    /**
     * Called when the algorithm produced an error
     * 
     * @param inputValues
     * @param errorMsg
     */
    default public void algorithmError(List<ParameterValue> inputValues, String errorMsg) {

    }

}
