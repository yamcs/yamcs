package org.yamcs.tctm;

import com.google.common.util.concurrent.Service;

/**
 * A sourcce of data into yamcs; Currently TM, TC and Parameter
 * 
 * @author nm
 *
 */
public interface Link extends Service {
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
        DISABLED;
    }

    /**
     * Returns the current link status.
     */
    public Status getLinkStatus();

    /**
     * @return more detailed status information
     */
    public String getDetailedStatus();

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

}
