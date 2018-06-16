package org.yamcs.ui;

import org.yamcs.protobuf.Yamcs.TmPacketData;

public interface PacketListener {
	public boolean isCanceled();
	public void exception(final Exception e);
	public void packetReceived(TmPacketData c);
	public void replayFinished();
}
