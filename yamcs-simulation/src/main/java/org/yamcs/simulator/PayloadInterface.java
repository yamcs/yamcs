package org.yamcs.simulator;

import java.nio.ByteBuffer;

public interface PayloadInterface {
	int getSize();
	int getMsgId();
	int getCRCSeed();
	ByteBuffer makeBuffer();
}
