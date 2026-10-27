package org.yamcs.tctm;

/** Applies physical-channel coding to an uplink frame. */
public interface TcChannelEncoder extends DataLinkComponent {

    byte[] encode(int virtualChannelId, byte[] data);
}
