package org.yamcs.algorithms;

import java.util.Map;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;

/**
 * Represents the execution context of one algorithm.
 * <p>
 * An AlgorithmExecutor is reused upon each update of one or more of its InputParameters.
 * 
 */
public interface AlgorithmExecutor {
    Algorithm getAlgorithm();

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
    boolean updateParameters(ParameterValueList currentDelivery);

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
    AlgorithmExecutionResult execute(long acqTime, long genTime) throws AlgorithmException;

    /**
     * 
     * @return the execution context in which the executor activates
     */
    AlgorithmExecutionContext getExecutionContext();


    /**
     * Called for an algorithm that is linked to command verification.
     * 
     * @param args
     *            - the values of the arguments sent
     * @return true if the algorithm should run
     */
    default boolean updateArguments(Map<Argument, ArgumentValue> args) {
        return false;
    }
}
