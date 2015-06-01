package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class CommandData {
	
	float timestamp;
	int commandOn;
	int commandOf;
	
	CommandData(CCSDSPacket packet) {
		
		ByteBuffer buffer = packet.getUserDataBuffer();
		
		commandOn = (int)buffer.get(0);
		commandOf = (int)buffer.get(1);
		
	}

	CommandData() {
	}

	public String toString() {
		return String.format("[COMMAND]");
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		
		buffer.put((byte) commandOn);
		buffer.put((byte) commandOf);
	
	}
	
	public void fillPacket(CCSDSPacket packet, int bufferOffset, int commandOn, int commandOf) {
		
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);
		
		buffer.put((byte) commandOn);
		buffer.put((byte) commandOf);
	}

    public static CCSDSPacket buildLosTransmittedRecordingPacket(String transmittedRecordName)
    {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 10);
        packet.appendUserDataBuffer(transmittedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);

        return packet;
    }

    public static CCSDSPacket buildLosDeletedRecordingPacket(String deletedRecordName)
    {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 11);
        packet.appendUserDataBuffer(deletedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);

        return packet;
    }


}
