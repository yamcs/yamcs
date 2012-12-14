package org.yamcs.ui;

import org.yamcs.utils.CcsdsPacket;

public interface PacketListener {
	public boolean isCanceled();
	public void exception(final Exception e);
	public void packetReceived(CcsdsPacket c);
	public void replayFinished();
}
