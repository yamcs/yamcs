package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;

class PowerData {
	float timestamp;
    int busStatus;
	float busVoltage, busCurrent, systemCurrent;
	float batteryVoltage1, batteryTemp1, batteryCapacity1;
	float batteryVoltage2, batteryTemp2, batteryCapacity2;
	float batteryVoltage3, batteryTemp3, batteryCapacity3;

	PowerData(CCSDSPacket packet) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		
		busStatus = buffer.get(0);
		
		busVoltage = (float)buffer.get(1);
		busCurrent = (float)buffer.get(2);
		systemCurrent = (float)buffer.get(3);

		batteryVoltage1 = (float)buffer.get(4);
		batteryTemp1 = (float)buffer.get(5);
		batteryCapacity1 = (float)buffer.getShort(6);

		batteryVoltage2 = (float)buffer.get(8);
		batteryTemp2 = (float)buffer.get(9);
		batteryCapacity2 = (float)buffer.getShort(10);

		batteryVoltage3 = (float)buffer.get(12);
		batteryTemp3 = (float)buffer.get(13);
		batteryCapacity3 = (float)buffer.getShort(14);
	}

	PowerData() {
	}

	@Override
    public String toString() {
		return String.format("[PowerData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		buffer.put((byte)busStatus);
		buffer.put((byte)busVoltage);
		buffer.put((byte)busCurrent);
		buffer.put((byte)systemCurrent);
		buffer.put((byte)batteryVoltage1);
		buffer.put((byte)batteryTemp1);
		buffer.putShort((short)batteryCapacity1);
		buffer.put((byte)batteryVoltage2);
		buffer.put((byte)batteryTemp2);
		buffer.putShort((short)batteryCapacity2);
		buffer.put((byte)batteryVoltage3);
		buffer.put((byte)batteryTemp3);
		buffer.putShort((short)batteryCapacity3);
	}
}
