package org.yamcs.tctm.ccsds;

import java.util.concurrent.Semaphore;

import org.yamcs.utils.TimeEncoding;

/**
 * Handlers uplink data in a virtual channel
 * 
 */
public interface VcUplinkHandler {
    /**
     * Retrieves the next frame in the Virtual Channel, or returns null if there is no frame available at the moment.
     * 
     * @return
     */
    TcTransferFrame getFrame();

    /**
     * Returns the timestamp of the first frame ready to be dispatched or {@link TimeEncoding#INVALID_INSTANT} if there
     * is no frame.
     * <p>
     * The timestamp is used by the {@link MasterChannelFrameMultiplexer} to select the Virtual Channel from which the
     * next frame is sent in case of FIFO priority scheme.
     * 
     * @return
     */
    long getFirstFrameTimestamp();

    /**
     * return the virtual channel parameters
     * 
     * @return
     */
    VcUplinkManagedParameters getParameters();

    /**
     * The semaphore will be used by the virtual channel to signal to {@link MasterChannelFrameMultiplexer} that data is
     * available to be uplinked
     * 
     * @param dataAvailableSemaphore
     */
    void setDataAvailableSemaphore(Semaphore dataAvailableSemaphore);

}
