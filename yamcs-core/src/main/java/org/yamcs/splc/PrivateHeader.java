package org.yamcs.splc;

import java.nio.ByteBuffer;

public class PrivateHeader {
	
	PrivateHeader(ByteBuffer data) {
		source=data.get();
		destination=data.get();
		cls=data.get();
		type=data.get();
	}
	public byte source;
	public byte destination;
	public byte cls; //"class" is a reserved word in java :(
	public byte type;
}
