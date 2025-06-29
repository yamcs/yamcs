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

    /**
     * Added in Yamcs 5.11.12 to allow a link to specify a pp stream it wants to push data to.
     * <p>
     * It is used by the {@link LinkManager#configureDataLink(Link, org.yamcs.YConfiguration)}.
     * <p>
     * If the method returns null, the link manager will use the value of the configuration "ppStream"
     */
    default String getPpStreamName() {
        return null;
    }
}
