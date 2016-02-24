package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

public interface AlgorithmExecListener {
    public void algorithmRun(Object returnValue, List<ParameterValue> outputValues);
}
