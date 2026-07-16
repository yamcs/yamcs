package org.yamcs.tctm.ccsds;

import java.util.Collection;

import org.yamcs.tctm.TcTmException;

/** Removes mission-specific outer layers before CCSDS transfer-frame decoding. */
public interface TmFrameDecapsulator {

    record DecapsulatedFrame(byte[] data, int offset, int length, Integer expectedVirtualChannelId) {
    }

    DecapsulatedFrame decapsulate(byte[] data, int offset, int length) throws TcTmException;

    /** Maximum number of bytes which may surround a CCSDS transfer frame. */
    int maxFrameOverhead();

    /** Validates the provider configuration against the parent CCSDS link. */
    default void validate(int maximumFrameLength, Collection<Integer> virtualChannelIds) {
    }
}
