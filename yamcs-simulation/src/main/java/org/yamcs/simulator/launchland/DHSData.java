package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;

class DHSData {
	float timestamp;
	float primBusVoltage1, primBusCurrent1;
	float primBusVoltage2, primBusCurrent2;
	float secBusVoltage2, secBusCurrent2;
	float secBusVoltage3, secBusCurrent3;

	DHSData(CCSDSPacket packet) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		primBusVoltage1 = (float)buffer.get(0);
		primBusCurrent1 = (float)buffer.get(1);
		primBusVoltage2 = (float)buffer.get(2);
		primBusCurrent2 = (float)buffer.get(3);
		secBusVoltage2 = (float)buffer.get(4);
		secBusCurrent2 = (float)buffer.get(5);
		secBusVoltage3 = (float)buffer.get(6);
		secBusCurrent3 = (float)buffer.get(7);
	}

	DHSData() {
	}

	@Override
    public String toString() {
		return String.format("[DHSData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);

		buffer.put((byte)primBusVoltage1);
		buffer.put((byte)primBusCurrent1);
		buffer.put((byte)primBusVoltage2);
		buffer.put((byte)primBusCurrent2);
		buffer.put((byte)secBusVoltage2);
		buffer.put((byte)secBusCurrent2);
		buffer.put((byte)secBusVoltage3);
		buffer.put((byte)secBusCurrent3);
		buffer.put((byte)0);
	}
}
