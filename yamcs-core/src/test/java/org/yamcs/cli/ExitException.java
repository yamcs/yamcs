package org.yamcs.cli;

@SuppressWarnings("serial")
public class ExitException extends RuntimeException {

    public ExitException() {
        // TODO Auto-generated constructor stub
    }

    public ExitException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public ExitException(Throwable cause) {
        super(cause);
    }

    public ExitException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExitException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
