package org.yamcs.algorithms;

import java.util.Arrays;
import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;

/**
 * The result of the algorithm execution consists of:
 * <ul>
 * <li>a list of input parameter values - optional - the list of parameters which have been used as input to the
 * algorithm.
 * <li>
 * <li>a list of output parameter values - these are {@link ParameterValue} which are then propagated to the Yamcs</li>
 * clients.
 * <li>a return value - this is not an output parameter; used for command verifiers</li>
 * </ul>
 * 
 * 
 * @author nm
 *
 */
public class AlgorithmExecutionResult {
    final List<RawEngValue> inputValues;
    final List<ParameterValue> outputValues;
    final Object returnValue;

    public AlgorithmExecutionResult(List<RawEngValue> inputValues, Object returnValue,
            List<ParameterValue> outputValues) {
        this.inputValues = inputValues;
        this.returnValue = returnValue;
        this.outputValues = outputValues;
    }

    public AlgorithmExecutionResult(Object returnValue, List<ParameterValue> outputValues) {
        this(null, returnValue, outputValues);
    }
    public AlgorithmExecutionResult(List<ParameterValue> outputValues) {
        this(null, null, outputValues);
    }

    /**
     * Constructor for an algorithm result which returns exactly one value
     * 
     * @param outputValue
     */
    public AlgorithmExecutionResult(ParameterValue outputValue) {
        this(null, null, Arrays.asList(outputValue));
    }
}
