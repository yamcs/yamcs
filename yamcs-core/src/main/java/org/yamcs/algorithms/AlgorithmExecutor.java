package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
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
     * use the method below instead which provides a list indexed by parameter
     * <p>
     * (the list can be large as it contains all parameters that are part of a delivery
     * (for example all parameters extracted from one packet)!)
     * 
     * @param paramList
     * @return
     */
    @Deprecated
    default boolean updateParameters(List<ParameterValue> paramList) {
        return false;
    }

    /**
     * This method is called each time new parameters are received (for example extracting them from a packet).
     * <p>
     * The list can be large, it will contain all parameters extracted from one packet but it is indexed per parameter.
     * <p>
     * The executor should copy its inputs if updated or should use the list to determine if it should
     * run.
     * 
     * @param currentDelivery
     *            - list of parameters part of the current delivery
     * @return true if the algorithm should run
     */
    default boolean updateParameters(ParameterValueList currentDelivery) {
        return updateParameters(new ArrayList<>(currentDelivery));
    }

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
