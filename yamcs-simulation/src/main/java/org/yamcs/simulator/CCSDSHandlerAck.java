package org.yamcs.simulator;

import java.nio.ByteBuffer;

class CCSDSHandlerAck extends CVSHandlerEPS
{
	private int currentEntry = 0;

	public void fillAckPacket(CCSDSPacket packet, int commandRecived)
	{
		AckData entry = new AckData();
		entry.fillPacket(packet, 0, commandRecived );
	}
	
	public void fillExeCompPacket(CCSDSPacket packet, int whichBat, int commandRecived){
	
	AckData entry = new AckData();
	
		switch(whichBat){
			case 1: whichBat = 1;
				entry.fillPacket(packet, 0, commandRecived );
			case 2: whichBat = 2;
				entry.fillPacket(packet, 1, commandRecived );
			case 3: whichBat = 3;
				entry.fillPacket(packet, 2, commandRecived );
		}
		
	}
}
