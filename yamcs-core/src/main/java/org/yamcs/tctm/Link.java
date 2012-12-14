package org.yamcs.tctm;
/**
 * A sourcce of data into yamcs; Currently TM, TC and Parameter
 * @author nm
 *
 */
public interface Link {
    /**
     * Returns one of "OK" or "UNAVAIL". 
     * It's sure what the difference between uplink status and forward link status is.
     * @return
     */
    public abstract String getLinkStatus();

    /**
     * @return more detailed status information
     */
    public abstract String getDetailedStatus();
    
    /**
     * Reenable the data transit if disabled by the disable() method. 
     */
    public abstract void enable();

    /**
     * Disable any data I/O through this link. 
     * Any connection to a server is closed. Can be reenabled using the enable method.
     * Note that this method can be called before starting the service if it's configured as such in the configuration file
     */
    public abstract void disable();

    public abstract boolean isDisabled();

    public abstract long getDataCount();

}