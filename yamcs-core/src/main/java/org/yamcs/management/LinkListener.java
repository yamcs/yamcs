package org.yamcs.management;

import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.tctm.Link;

/**
 * Used by LinkManager to distribute data link related updates
 */
public interface LinkListener {

    /**
     * A new link was added
     */
    void linkAdded(Link link);

    /**
     * An existing link was removed
     */
    void linkRemoved(Link link);

    /**
     * Implement {@link linkAdded} instead.
     */
    @Deprecated
    default void linkRegistered(LinkInfo linkInfo) {
    }

    /**
     * Implement {@link linkRemoved} instead.
     */
    @Deprecated
    default void linkUnregistered(LinkInfo linkInfo) {
    }
}
