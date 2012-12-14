package org.yamcs.xtce;

public class DatabaseLoadException extends Exception {
	public DatabaseLoadException(Exception originalException) {
		super(originalException);
	}

	public DatabaseLoadException(String msg) {
        super(msg);
    }
    
}
