package org.yamcs.tctm;

import org.yamcs.YConfiguration;

/**
 * A source of data into yamcs; Currently TM, TC and Parameter
 * 
 * @author nm
 *
 */
public interface Link {
    public enum Status {
        /**
         * the link is up ready to receive data.
         */
        OK,
        /**
         * the link is down although it should be up; for instance a TCP client that cannot connect to the remote
         * server.
         */
        UNAVAIL,
        /**
         * the link has been disabled by the user (so it's implicitly unavailable)
         */
        DISABLED,
        /**
         * the link has failed (like an internal crash while processing the data)
         */
        FAILED;

    }

    /**
     * Returns the current link status.
     */
    public Status getLinkStatus();

    /**
     * @return more detailed status information
     */
    default String getDetailedStatus() {
        return null;
    }

    /**
     * Reenable the data transit if disabled by the disable() method.
     */
    public void enable();

    /**
     * Disable any data I/O through this link. Any connection to a server is closed. Can be reenabled using the enable
     * method. Note that this method can be called before starting the service if it's configured as such in the
     * configuration file
     */
    public void disable();

    public boolean isDisabled();

    public long getDataInCount();

    public long getDataOutCount();

    public void resetCounters();

    /**
     * Return the name of the link
     */
    public String getName();

    /**
     * 
     * @return the config (args) used when creating the link
     */
    public YConfiguration getConfig();

    /**
     * If this link is a sublink of an aggregated link, get the parent link.
     */
    default AggregatedDataLink getParent() {
        return null;
    }

    /**
     * Set the parent link if this is a sublink of an aggregated link.
     */
    default void setParent(AggregatedDataLink parent) {
    }
}
