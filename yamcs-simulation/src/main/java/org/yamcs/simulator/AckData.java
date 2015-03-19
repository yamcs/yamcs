package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class AckData {
	
	float timestamp;
	
	int commandReceived;
	
	

	
		
	AckData(CCSDSPacket packet) {
		
		ByteBuffer buffer = packet.getUserDataBuffer();
		
		commandReceived = (int)buffer.get(0);
		
	}

	AckData() {
	}

	public String toString() {
		return String.format("[AckData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset, int commandReceived)
	{
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		
		buffer.put((byte) commandReceived);
			
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
