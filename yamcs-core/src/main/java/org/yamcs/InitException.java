package org.yamcs;

import org.yamcs.YamcsException;

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
