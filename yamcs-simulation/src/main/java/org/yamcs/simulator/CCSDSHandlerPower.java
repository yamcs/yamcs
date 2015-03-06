package org.yamcs.simulator;

import java.nio.ByteBuffer;

class CCSDSHandlerPower extends CSVHandlerPower
{
	private int currentEntry = 0;

	public void fillPacket(CCSDSPacket packet)
	{
		if (getNumberOfEntries() == 0) return;

		if (currentEntry >= getNumberOfEntries()) {
			currentEntry = 0;
		}

		PowerData entry = entries.elementAt(currentEntry++);
		entry.fillPacket(packet, 0);
	}
	
	
	public void setBattOneOff(CCSDSPacket packet){

		ByteBuffer buffer = packet.getUserDataBuffer();

		buffer.put(3,(byte)0);
		buffer.put(4,(byte)0);
		buffer.putShort(5,(short)0);
		
	}

	public void setBattTwoOff(CCSDSPacket packet){

		ByteBuffer buffer = packet.getUserDataBuffer();

		buffer.put(7,(byte)0);
		buffer.put(8,(byte)0);
		buffer.putShort(9,(short)0);

	}

	public void setBattThreeOff(CCSDSPacket packet){

		ByteBuffer buffer = packet.getUserDataBuffer();

		buffer.put(11,(byte)0);
		buffer.put(12,(byte)0);
		buffer.putShort(13,(short)0);

	}
	
	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
		ByteBuffer buffer = packet.getUserDataBuffer();

		buffer.position(bufferOffset);

		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.putShort((short)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.putShort((short)0);
		buffer.put((byte)0);
		buffer.put((byte)0);
		buffer.putShort((short)0);

	}

	
 public void displayUserData(CCSDSPacket packet){
	 
	     ByteBuffer buffer = packet.getUserDataBuffer();
	 
		 buffer.position(16);
		 
		 for( int i = 0; i < buffer.capacity(); i++){
			 
			 System.out.print(buffer.get(i) + " : ");
			 
		 }
		 System.out.print(" \n ");
		 
	 }

	
}
