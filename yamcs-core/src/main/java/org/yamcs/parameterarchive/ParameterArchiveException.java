package org.yamcs.parameterarchive;

public class ParameterArchiveException extends RuntimeException {
    public ParameterArchiveException(String message) {
        super(message);
    }
    
    public ParameterArchiveException(String message, Throwable t) {
        super(message, t);
    }
}
