package org.yamcs.tctm;

import org.yamcs.TmPacket;
import org.yamcs.management.LinkManager;

/**
 * 
 * Interface for components reading packets from external parties.
 * <p>
 * 
 * The tm link should push {@link TmPacket} objects to the passed {@link TmSink}. The TmSink is implemented usually by
 * the {@link LinkManager}; it takes care of putting these packets on the configured stream.
 * 
 */
public interface TmPacketDataLink extends Link {
    /**
     * sets the tm sink that should get all the tm packets
     * 
     */
    public void setTmSink(TmSink tmSink);

    /**
     * This method has been introduced to allow classes that implement multiple links (e.g. TM and TC) to not
     * effectively support one ore more of them (depending on configuration)
     * <p>
     * If this method returns false, the {@link LinkManager} skips the link configuration for TM purposes
     */
    default boolean isTmPacketDataLinkImplemented() {
        return true;
    }
}
