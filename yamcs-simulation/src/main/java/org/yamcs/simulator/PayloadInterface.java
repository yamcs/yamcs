package org.yamcs.simulator;

import java.nio.ByteBuffer;

interface PayloadInterface
{
	public int getSize();
	public int getMsgId();
	public int getCRCSeed();
	public ByteBuffer makeBuffer();
}
