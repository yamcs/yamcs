package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

/**
 * Represents the execution context of one algorithm. An AlgorithmExecutor is reused
 * upon each update of one or more of its InputParameters.
 *  
 */
public interface AlgorithmExecutor {
    Algorithm getAlgorithm();

    Set<Parameter> getRequiredParameters();

    int getLookbackSize(Parameter parameter);
    
    /**
     * Update parameters and return true if the algorithm should run
     * 
     * @param paramList - list of input parameters
     * @return true if the algorithm should run
     */
    boolean updateParameters(ArrayList<ParameterValue> paramList);
    /**
     * Runs the associated algorithm with the latest InputParameters
     * @param acqTime 
     * @param genTime 
     * @return the outputted parameters, if any
     */
    List<ParameterValue> runAlgorithm(long acqTime, long genTime);

    void addExecListener(AlgorithmExecListener listener);

    AlgorithmExecutionContext getExecutionContext();
}