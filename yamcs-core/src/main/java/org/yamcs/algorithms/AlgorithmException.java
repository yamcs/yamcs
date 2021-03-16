package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

/**
 * exception thrown when unexpected things happen during the loading and execution of algorithms.
 * 
 * @author nm
 *
 */
@SuppressWarnings("serial")
public class AlgorithmException extends RuntimeException {
    List<ParameterValue> inputValues;

    public AlgorithmException(String message) {
        super(message);
    }

    /**
     * When the error was encountered when executing an algorithm with the given inputs
     * 
     * @param inputValues
     * @param message
     */
    public AlgorithmException(List<ParameterValue> inputValues, String message) {
        super(message);
    }

    public AlgorithmException(String message, Throwable cause) {
        super(message, cause);
    }

    public AlgorithmException(Throwable cause) {
        super(cause);
    }
}
