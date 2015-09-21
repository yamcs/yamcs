package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;

public class EpsLVPDUData {
	
	float timestamp;
	float LVPDUStatus;
	float LVPDUVoltage;
	
	EpsLVPDUData(CCSDSPacket packet) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		LVPDUStatus = (float)buffer.get(0);
		LVPDUVoltage = (float)buffer.get(1);
	}

	EpsLVPDUData() {
	}

	@Override
    public String toString() {
		return String.format("[EpsLVPDUData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		
		buffer.put((byte) LVPDUStatus);
		buffer.put((byte) LVPDUVoltage);
	}
}
