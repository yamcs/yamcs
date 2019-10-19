package org.yamcs.tctm.ccsds;

/**
 * Called from the {@link MasterChannelFrameHandler} to handle TM frames for a specific virtual channel.
 * 
 * @author nm
 *
 */
public interface VcDownlinkHandler {

    void handle(DownlinkTransferFrame frame);

}
