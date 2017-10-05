package org.yamcs.xtceproc;

import java.util.List;
import java.util.Set;

import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.parameter.ParameterValue;
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

    public int getLookbackSize(Parameter parameter) {
        return 0;
    }
    
    /**
     * Update parameters and return true if the algorithm should run
     * 
     * @param paramList - list of input parameters
     * @return true if the algorithm should run
     */
    public boolean updateParameters(List<ParameterValue> paramList) {
        return false;
    }
  
    /**
     * Runs the associated algorithm with the latest InputParameters
     * @param acqTime 
     * @param genTime 
     * @return the outputted parameters, if any
     */
    public List<ParameterValue> runAlgorithm(long acqTime, long genTime) {
        return null;
    }

    public void addExecListener(AlgorithmExecListener listener) {
        
    }

    public AlgorithmExecutionContext getExecutionContext() {
        return null;
    }
}
