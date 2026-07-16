package org.yamcs.tctm.ccsds;

import java.util.Collection;

import org.yamcs.commanding.PreparedCommand;

/**
 * Adds mission-specific outer layers to an encoded CCSDS uplink transfer frame.
 * <p>
 * Implementations are invoked after CCSDS framing (including SDLS and the frame error control field) and before any
 * configured CLTU encoding.
 */
public interface TcFrameEncapsulator {

    /**
     * Returns a key identifying commands that may be placed in the same transfer frame.
     */
    default Object getAggregationKey(PreparedCommand command) {
        return null;
    }

    /**
     * Adds the configured outer layers to a completed transfer frame.
     */
    byte[] encapsulate(UplinkTransferFrame frame);

    /**
     * Validates the provider configuration against the parent CCSDS link.
     */
    default void validate(int maximumFrameLength, Collection<Integer> virtualChannelIds) {
    }
}
