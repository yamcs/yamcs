package org.yamcs.yarch;

public class YarchException extends Exception {
	public YarchException(String string) {
		super(string);
	}
	public YarchException(Throwable t) {
		super(t);
	}
}
