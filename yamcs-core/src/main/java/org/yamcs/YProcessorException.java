package org.yamcs;

public class YProcessorException extends Exception {

	public YProcessorException(String s) {
		super(s);
	}

    public YProcessorException(String message, Throwable t) {
        super(message,t);
    }

}
