package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

/**
 * Represents the execution context of one algorithm.
 * <p>
 * An AlgorithmExecutor is reused upon each update of one or more of its InputParameters.
 * 
 */
public interface AlgorithmExecutor {
    Algorithm getAlgorithm();

    /**
     * Update parameters and return true if the algorithm should run
     * 
     * @param paramList
     *            - list of input parameters
     * @return true if the algorithm should run
     */
    boolean updateParameters(List<ParameterValue> paramList);

    /**
     * Runs the associated algorithm with the latest InputParameters
     * 
     * @param acqTime
     * @param genTime
     * @return the outputted parameters, if any
     */
    List<ParameterValue> runAlgorithm(long acqTime, long genTime);

    /**
     * Add a listener to be called each time the algorithm runs
     * 
     * @param listener
     */
    default void addExecListener(AlgorithmExecListener listener) {
    }

    /**
     * Remove the listener from the list to be called each time the algorithm runs
     * 
     * @param listener
     */
    default void removeExecListener(AlgorithmExecListener listener) {
    }

    /**
     * 
     * @return the execution context in which the executor activates
     */
    AlgorithmExecutionContext getExecutionContext();

    /**
     * the method has been removed because it has nothing to do here.
     * The implementation from AbstractAlgorithmExecutor has been moved in {@link AlgorithmUtils}
     */
    @Deprecated
    default int getLookbackSize(Parameter parameter) {
        return 0;
    }
}
