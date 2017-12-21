package org.yamcs.oldparchive;

public class ParameterArchiveException extends RuntimeException {
    public ParameterArchiveException(String message) {
        super(message);
    }
    
    public ParameterArchiveException(String message, Throwable t) {
        super(message, t);
    }
}
