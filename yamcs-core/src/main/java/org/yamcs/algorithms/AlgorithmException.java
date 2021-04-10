package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.parameter.RawEngValue;

/**
 * exception thrown when unexpected things happen during the loading and execution of algorithms.
 * 
 */
@SuppressWarnings("serial")
public class AlgorithmException extends RuntimeException {
    List<RawEngValue> inputValues;

    public AlgorithmException(String message) {
        super(message);
    }

    /**
     * When the error was encountered when executing an algorithm with the given inputs
     * 
     * @param inputValues
     * @param message
     */
    public AlgorithmException(List<RawEngValue> inputValues, String message) {
        super(message);
        this.inputValues = inputValues;
    }

    public AlgorithmException(String message, Throwable cause) {
        super(message, cause);
    }

    public AlgorithmException(Throwable cause) {
        super(cause);
    }
}
