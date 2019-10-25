package org.yamcs.tctm.ccsds;
/**
 * Handles idle frames by ignoring them.
 * @author nm
 *
 */
public class IdleFrameHandler implements VcDownlinkHandler {

    @Override
    public void handle(DownlinkTransferFrame frame) {
    //do nothing
    }

}
