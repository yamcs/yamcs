package org.yamcs.simulator;

import java.nio.ByteBuffer;

class CCSDSHandlerEPSLVPDU extends CVSHandlerEPS
{
	private int currentEntry = 0;

	public void fillPacket(CCSDSPacket packet)
	{
		if (getNumberOfEntries() == 0) return;

		if (currentEntry >= getNumberOfEntries()) {
			currentEntry = 0;
		}

		EpsLVPDUData entry = entries.elementAt(currentEntry++);
		entry.fillPacket(packet, 0);
	}
}
