package org.yamcs.xtceproc;

import java.util.Set;

import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

/**
 * stubbed implementation of DataDecoder that "removes" all the AlgorithmExecutor methods - 
 *  to be used until the data decoders will work as algorithms, or for data decoders that do not need any input parameters  
 * 
 * @author nm
 *
 */
public abstract class AbstractDataDecoder implements DataDecoder {
    public Algorithm getAlgorithm() {
        return null;
    }

    public Set<Parameter> getRequiredParameters() {
        return null;
    }
    
    /**
     * Update parameters and return true if the algorithm should run
     * 
     * @param paramList - list of input parameters
     * @return true if the algorithm should run
     */
    public boolean updateParameters(ParameterValueList paramList) {
        return false;
    }
  
    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime) {
        throw new IllegalStateException("Cannot run this method on a data decoder algorithm");
    }

    public void addExecListener(AlgorithmExecListener listener) {
    }

    public void removeExecListener(AlgorithmExecListener listener) {
    }

    public AlgorithmExecutionContext getExecutionContext() {
        return null;
    }
}
