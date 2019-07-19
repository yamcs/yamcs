package org.yamcs;

@SuppressWarnings("serial")
public class ProcessorException extends YamcsException {

    public ProcessorException(String s) {
        super(s);
    }

    public ProcessorException(String message, Throwable t) {
        super(message, t);
    }
}
