package org.yamcs.mdb;

import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.xtce.Algorithm;

/**
 * stubbed implementation of DataDecoder that "removes" all the AlgorithmExecutor methods -
 * to be used until the data decoders will work as algorithms, or for data decoders that do not need any input
 * parameters
 * 
 * @author nm
 *
 */
public abstract class AbstractDataDecoder implements DataDecoder {
    public Algorithm getAlgorithm() {
        return null;
    }

    @Override
    public boolean update(ProcessingData data) {
        return false;
    }

    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
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
