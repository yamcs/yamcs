package org.yamcs.parameter;

public class NoProviderException extends RuntimeException {

    public NoProviderException() {
	super();
    }

    public NoProviderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
	super(message, cause, enableSuppression, writableStackTrace);
    }

    public NoProviderException(String message, Throwable cause) {
	super(message, cause);
    }

    public NoProviderException(String message) {
	super(message);
    }

    public NoProviderException(Throwable cause) {
	super(cause);
    }
    
}
