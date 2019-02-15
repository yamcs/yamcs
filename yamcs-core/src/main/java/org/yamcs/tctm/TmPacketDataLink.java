package org.yamcs.tctm;

/**
 * 
 * @author nm
 * 
 * Interface for components reading packets from external parties.
 * 
 */
public interface TmPacketDataLink extends Link {
    /**
     * sets the tm processor that should get all the tm packets
     * @param tmSink 
     */
    public void setTmSink(TmSink tmSink);
}
