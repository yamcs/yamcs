package org.yamcs;

import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmSink;
import org.yamcs.xtce.SequenceContainer;

/**
 * 
 * @author nm
 *
 * Generic interface for components processing packets (usually to transform them into parameters).
 * The transformation according to the XTCE standard needs to know which root container to start from.
 *  
 *  The packets are provided by the TmPacketProvider
 */
public interface TmProcessor extends TmSink {
    /**
     * processes packets derived from the given root container
     * @param pwrt
     * @param rootContainer
     */
    public void processPacket(PacketWithTime pwrt, SequenceContainer rootContainer);
    
    /**
     * Notification that there is no more packet to process
     */
    public void finished() ;

   
}
