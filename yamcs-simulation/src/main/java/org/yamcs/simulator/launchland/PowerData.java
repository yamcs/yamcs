package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;

class PowerData
{
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

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
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

	
/*	void sendMavlinkPackets(UplinkInterface uplink)
	{
		final int compid = 2; // power
		MavlinkParameterValue value = new MavlinkParameterValue();
		value.param_count = 12;

		value.param_value = new Float(busStatus);
		value.param_index = 0;
		value.param_id = "BusStatus";
		uplink.sendMessage(value, compid);
		
		value.param_value = new Float(busVoltage);
		value.param_index = 1;
		value.param_id = "BusVoltage";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(busCurrent);
		value.param_index = 2;
		value.param_id = "BusCurrent";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(systemCurrent);
		value.param_index = 3;
		value.param_id = "SystemCurrent";
		uplink.sendMessage(value, compid);

		//

		value.param_value = new Float(batteryVoltage1);
		value.param_index = 4;
		value.param_id = "BatteryVoltage1";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryTemp1);
		value.param_index = 5;
		value.param_id = "BatteryTemp2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryCapacity1);
		value.param_index = 6;
		value.param_id = "BatteryCapacity1";
		uplink.sendMessage(value, compid);

		//

		value.param_value = new Float(batteryVoltage2);
		value.param_index = 7;
		value.param_id = "BatteryVoltage2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryTemp2);
		value.param_index = 8;
		value.param_id = "BatteryTemp2";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryCapacity2);
		value.param_index = 9;
		value.param_id = "BatteryCapacity2";
		uplink.sendMessage(value, compid);

		//

		value.param_value = new Float(batteryVoltage3);
		value.param_index = 10;
		value.param_id = "BatteryVoltage3";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryTemp3);
		value.param_index = 11;
		value.param_id = "BatteryTemp3";
		uplink.sendMessage(value, compid);

		value.param_value = new Float(batteryCapacity3);
		value.param_index = 12;
		value.param_id = "BatteryCapacity3";
		uplink.sendMessage(value, compid);
	}*/
}
