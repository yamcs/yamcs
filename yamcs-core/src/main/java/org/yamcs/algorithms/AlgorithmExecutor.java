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
     * Runs the associated algorithm with the latest InputParameters.
     * <p>
     * From Yamcs 5.4.3 this should throw an exception if there is an error within the algorithm.
     * <p>
     * The error message and error count will be remembered and available to external clients via the API.
     * 
     * @deprecated
     *             Please use the {@link #execute(long, long)} instead
     * @param acqTime
     * @param genTime
     * @return the output parameters, if any
     * 
     * 
     */
    @Deprecated
    default List<ParameterValue> runAlgorithm(long acqTime, long genTime) {
        throw new IllegalStateException("Please implement the execute method");
    }

    /**
     * Runs the associated algorithm with the latest InputParameters.
     * <p>
     * Should throw an exception if there is an error within the algorithm.
     * <p>
     * The error message and error count will be remembered and available to external clients via the API.
     * 
     * @param acqTime
     * @param genTime
     * @return the output parameters, if any
     * 
     */
    default AlgorithmExecutionResult execute(long acqTime, long genTime) throws AlgorithmException {
        List<ParameterValue> outputValues = runAlgorithm(acqTime, genTime);
        return new AlgorithmExecutionResult(outputValues);
    }

    /**
     * Add a listener to be called each time the algorithm runs
     * 
     * @deprecated
     *             Listeners are handled by {@link ActiveAlgorithm}
     * @param listener
     */
    @Deprecated
    default void addExecListener(AlgorithmExecListener listener) {
    }

    /**
     * Remove the listener from the list to be called each time the algorithm runs
     *
     * @deprecated
     *             Listeners are handled by {@link ActiveAlgorithm}
     * 
     * @param listener
     */
    @Deprecated
    default void removeExecListener(AlgorithmExecListener listener) {
    }

    /**
     * 
     * @return the execution context in which the executor activates
     */
    AlgorithmExecutionContext getExecutionContext();

    /**
     * @deprecated
     *             the method has been removed because it has nothing to do here.
     *             <p>
     *             The implementation from AbstractAlgorithmExecutor has been moved in {@link AlgorithmUtils}
     */
    @Deprecated
    default int getLookbackSize(Parameter parameter) {
        return 0;
    }
}
