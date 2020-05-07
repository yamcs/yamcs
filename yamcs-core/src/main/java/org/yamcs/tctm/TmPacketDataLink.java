package org.yamcs.tctm;

import org.yamcs.TmPacket;
import org.yamcs.management.LinkManager;

/**
 * 
 *         Interface for components reading packets from external parties.
 *         <p>
 * 
 *         The tm link should push {@link TmPacket} objects to the passed {@link TmSink}.
 *         The TmSink is implemented usually by the {@link LinkManager}; it takes care of putting these packets on the
 *         configured stream.
 *
 * @author nm
 */
public interface TmPacketDataLink extends Link {
    /**
     * sets the tm processor that should get all the tm packets
     * 
     * @param tmSink
     */
    public void setTmSink(TmSink tmSink);
}
