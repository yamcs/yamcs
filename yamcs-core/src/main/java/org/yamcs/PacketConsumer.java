package org.yamcs;

import java.nio.ByteBuffer;


public interface PacketConsumer {
	void processPacket(ItemIdPacketConsumerStruct iipcs, ByteBuffer bb);
}

