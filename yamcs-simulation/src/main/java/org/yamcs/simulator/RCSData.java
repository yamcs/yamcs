package org.yamcs.simulator;

import java.nio.ByteBuffer;

class RCSData
{
	float timestamp;
	float H2TankFill, H2TankTemp, H2TankPressure, H2ValveTemp, H2ValvePressure;
	float O2TankFill, O2TankTemp, O2TankPressure, O2ValveTemp, O2ValvePressure;
	float TurbineTemp, TurbinePressure;

	RCSData(CCSDSPacket packet) {
		ByteBuffer buffer = packet.getUserDataBuffer();

		H2TankFill = buffer.getFloat(0);
		O2TankFill = buffer.getFloat(4);
		H2TankTemp = (float)buffer.getShort(8);
		O2TankTemp = (float)buffer.getShort(10);

		H2TankPressure = buffer.getFloat(12);
		O2TankPressure = buffer.getFloat(16);
		H2ValveTemp = (float)buffer.getShort(20);
		O2ValveTemp = (float)buffer.getShort(22);

		H2ValvePressure = buffer.getFloat(24);
		O2ValvePressure = buffer.getFloat(28);
	}

	RCSData() {
	}

	public String toString() {
		return String.format("[RCSData]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);

		buffer.putFloat(H2TankFill);
		buffer.putFloat(O2TankFill);
		buffer.putShort((short)H2TankTemp);
		buffer.putShort((short)O2TankTemp);
		buffer.putFloat(H2TankPressure);
		buffer.putFloat(O2TankPressure);
		buffer.putShort((short)H2ValveTemp);
		buffer.putShort((short)O2ValveTemp);
		buffer.putFloat(H2ValvePressure);
		buffer.putFloat(O2ValvePressure);
		buffer.putShort((short)0);
		buffer.putShort((short)0);
	}

/*	void sendMavlinkPackets(UplinkInterface uplink)
	{
		final int compid = 3; // RHS
		MavlinkParameterValue value = new MavlinkParameterValue();
		value.param_count = 12;

		// H2

		value.param_value = new Float(H2TankFill);
		value.param_index = 0;
		value.param_id = "H2TankFill";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(H2TankTemp);
		value.param_index = 1;
		value.param_id = "H2TankTemp";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(H2TankPressure);
		value.param_index = 2;
		value.param_id = "H2TankPressure";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(H2ValveTemp);
		value.param_index = 3;
		value.param_id = "H2ValveTemp";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(H2ValvePressure);
		value.param_index = 4;
		value.param_id = "H2ValvePressure";
		uplink.sendMessage(value, compid);

		// O2

		value.param_value = new Float(O2TankFill);
		value.param_index = 5;
		value.param_id = "O2TankFill";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(O2TankTemp);
		value.param_index = 6;
		value.param_id = "O2TankTemp";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(O2TankPressure);
		value.param_index = 7;
		value.param_id = "O2TankPressure";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(O2ValveTemp);
		value.param_index = 8;
		value.param_id = "O2ValveTemp";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(O2ValvePressure);
		value.param_index = 9;
		value.param_id = "O2ValvePressure";
		uplink.sendMessage(value, compid);

		// Turbine

		value.param_value = new Float(TurbineTemp);
		value.param_index = 10;
		value.param_id = "TurbineTemp";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(TurbinePressure);
		value.param_index = 11;
		value.param_id = "TurbinePressure";
		uplink.sendMessage(value, compid);
	}*/
}
