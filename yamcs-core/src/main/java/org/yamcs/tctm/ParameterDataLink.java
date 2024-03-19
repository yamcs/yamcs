package org.yamcs.tctm;

import org.yamcs.management.LinkManager;

/**
 * Interface for components providing parameters aquired from external systems.
 *
 */
public interface ParameterDataLink extends Link {
    public void setParameterSink(ParameterSink parameterSink);

    /**
     * This method has been introduced to allow classes that implement multiple links (e.g. TM and TC) to not
     * effectively support one ore more of them (depending on configuration)
     * <p>
     * If this method returns false, the {@link LinkManager} skips the link configuration for TM purposes
     */
    default boolean isParameterDataLinkImplemented() {
        return true;
    }
}
