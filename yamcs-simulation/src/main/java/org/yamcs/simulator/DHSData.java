package org.yamcs.simulator;

import java.nio.ByteBuffer;

class DHSData
{
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

	public String toString() {
		return String.format("[DHSData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
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

/*	void sendMavlinkPackets(UplinkInterface uplink)
	{
		final int compid = 1; // DHS
		MavlinkParameterValue value = new MavlinkParameterValue();
		value.param_count = 8;

		value.param_value = new Float(primBusVoltage1);
		value.param_index = 0;
		value.param_id = "PrimBusVoltage1";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(primBusCurrent1);
		value.param_index = 1;
		value.param_id = "PrimBusCurrent1";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(primBusVoltage2);
		value.param_index = 2;
		value.param_id = "PrimBusVoltage2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(primBusCurrent2);
		value.param_index = 3;
		value.param_id = "PrimBusCurrent2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(secBusVoltage2);
		value.param_index = 4;
		value.param_id = "SecBusVoltage2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(secBusCurrent2);
		value.param_index = 5;
		value.param_id = "SecBusCurrent2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(secBusVoltage3);
		value.param_index = 6;
		value.param_id = "SecBusVoltage3";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(secBusCurrent3);
		value.param_index = 7;
		value.param_id = "SecBusCurrent3";
		uplink.sendMessage(value, compid);
	}*/
}

