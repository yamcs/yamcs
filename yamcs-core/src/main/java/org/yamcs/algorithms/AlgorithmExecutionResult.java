package org.yamcs.algorithms;

import java.util.Arrays;
import java.util.List;

import org.yamcs.commanding.VerificationResult;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;

/**
 * Describes the result of the algorithm execution, which consists of the following components:
 * <ul>
 * <li><b>Input Parameter Values (Optional)</b> - A list of parameters that were used as input to the algorithm.</li>
 * <li><b>Output Parameter Values</b> - A list of {@link ParameterValue} objects that are propagated to Yamcs clients.
 * </li>
 * <li><b>Return Value</b> - This is not an output parameter but is specifically used for command verifiers (see the
 * details below).</li>
 * </ul>
 * 
 * <h3>Command Verifier Return Value</h3>
 * <p>
 * In older versions of Yamcs, the command verifier would return either a boolean value (True) indicating success or a
 * String indicating failure. While this approach is still supported, the current preferred method is to use the custom
 * {@link VerificationResult} class, which provides a more suitable representation of the verifier's result.
 * </p>
 */
public class AlgorithmExecutionResult {
    private final List<RawEngValue> inputValues;
    private final List<ParameterValue> outputValues;
    private final Object returnValue;

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

    public List<RawEngValue> getInputValues() {
        return inputValues;
    }

    public List<ParameterValue> getOutputValues() {
        return outputValues;
    }

    public Object getReturnValue() {
        return returnValue;
    }
}
