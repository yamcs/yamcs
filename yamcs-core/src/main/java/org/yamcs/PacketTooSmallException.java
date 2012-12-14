package org.yamcs;

public class PacketTooSmallException extends Exception {
	public PacketTooSmallException(String s) {
		super(s);
	}

	private static final long serialVersionUID = -1229613858652488019L;
}
