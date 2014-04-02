package org.yamcs.xtce;

public class DatabaseLoadException extends RuntimeException {
    private static final long serialVersionUID = 4340483863669012865L;

    public DatabaseLoadException(Exception originalException) {
		super(originalException);
	}

	public DatabaseLoadException(String msg) {
	    super(msg);
    }
    
	public DatabaseLoadException(String msg, Throwable t) {
	    super(msg, t);
	}
}
