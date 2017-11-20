package org.yamcs;

public class ProcessorException extends YamcsException {

    public ProcessorException(String s) {
        super(s);
    }

    public ProcessorException(String message, Throwable t) {
        super(message,t);
    }

}
