package org.yamcs.tctm.ccsds;

import java.util.Collection;

import org.yamcs.tctm.TcTmException;

/** Removes mission-specific outer layers before CCSDS transfer-frame decoding. */
public interface TmFrameDecapsulator {

    record DecapsulatedFrame(byte[] data, int offset, int length, Collection<Integer> expectedVirtualChannelIds) {
    }

    /**
     * Removes mission-specific outer layers
     */
    DecapsulatedFrame decapsulate(byte[] data, int offset, int length) throws TcTmException;

    /** Maximum number of bytes which may surround an inner transfer frame. */
    int maxFrameOverhead();

    /** Validates the provider configuration against the parent data link. */
    default void validate(int maximumFrameLength, Collection<Integer> virtualChannelIds) {
    }
}
