package org.yamcs.algorithms;

/**
 * exception thrown when unexpected things happen during the loading and execution of algorithms.
 * 
 * @author nm
 *
 */
public class AlgorithmException extends RuntimeException {
    public AlgorithmException(String message) {
        super(message);
    }
    public AlgorithmException(String message, Throwable cause) {
        super(message, cause);
    }
    public AlgorithmException(Throwable cause) {
        super(cause);
    }

}
