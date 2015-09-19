package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

public interface PayloadInterface {
	int getSize();
	int getMsgId();
	int getCRCSeed();
	ByteBuffer makeBuffer();
}
