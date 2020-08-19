package org.yamcs;

@SuppressWarnings("serial")
public class InitException extends YamcsException {

    public InitException(String message) {
        super(message);
    }

    public InitException(String message, Throwable t) {
        super(message, t);
    }

    public InitException(Throwable t) {
        super(t);
    }
}
