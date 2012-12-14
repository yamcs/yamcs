package org.yamcs;

public class ChannelException extends Exception {

	public ChannelException(String s) {
		super(s);
	}

    public ChannelException(String message, Throwable t) {
        super(message,t);
    }

}
