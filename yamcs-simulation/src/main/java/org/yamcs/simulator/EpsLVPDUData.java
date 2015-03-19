package org.yamcs.simulator;

import java.nio.ByteBuffer;

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

	public String toString() {
		return String.format("[EpsLVPDUData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		
		buffer.put((byte) LVPDUStatus);
		buffer.put((byte) LVPDUVoltage);

		
	}

/*	void sendMavlinkPackets(UplinkInterface uplink)
	{
		final int compid = 4; // power
		MavlinkParameterValue value = new MavlinkParameterValue();
		value.param_count = 2;

		value.param_value = new Float(LVPDUStatus);
		value.param_index = 0;
		value.param_id = "LVPDUStatus";
		uplink.sendMessage(value, compid);
		
		value.param_value = new Float(LVPDUVoltage);
		value.param_index = 1;
		value.param_id = "LVPDUvoltage";
		uplink.sendMessage(value, compid);

	}*/

}
