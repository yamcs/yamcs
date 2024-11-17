package org.yamcs.parameterarchive;

/**
 * 
 * Use this in the consumers to signal that they don't want anymore data. Any suggestion for a less ugly way to achive
 * the same result is welcome.
 * 
 */
public class ConsumerAbortException extends RuntimeException {
    private static final long serialVersionUID = 1L;
}
