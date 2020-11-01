package org.yamcs.utils;

/**
 * Auxiliary class for GPS CCSDS time.
 * 
 * @author mu
 *
 */
public class GpsCcsdsTime {
    public final int coarseTime;
    public final byte fineTime;
    
    public GpsCcsdsTime(int coarseTime, byte fineTime) {
        this.coarseTime = coarseTime;
        this.fineTime = fineTime;
    }
}
