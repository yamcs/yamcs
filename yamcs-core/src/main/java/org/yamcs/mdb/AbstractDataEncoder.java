package org.yamcs.mdb;

import java.util.Set;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

/**
 * stubbed implementation of DataEncoder that "removes" all the AlgorithmExecutor methods -
 * to be used until the data decoders will work as algorithms, or for data decoders that do not need any input
 * parameters
 * 
 * @author nm
 *
 */
public abstract class AbstractDataEncoder implements DataEncoder {
    public Algorithm getAlgorithm() {
        return null;
    }

    public Set<Parameter> getRequiredParameters() {
        return null;
    }

    public int getLookbackSize(Parameter parameter) {
        return 0;
    }

    /**
     * Update the input data and return true if the algorithm should run
     * 
     * @return true if the algorithm should run
     */
    public boolean update(ProcessingData data) {
        return false;
    }

    /**
     * Runs the associated algorithm with the latest InputParameters
     */
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
        throw new IllegalStateException("Cannot run this method on a data encoder");
    }

    public AlgorithmExecutionContext getExecutionContext() {
        return null;
    }
}
