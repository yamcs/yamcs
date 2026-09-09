package org.yamcs.tctm;

import java.nio.ByteBuffer;

/** Removes physical-channel coding from a downlink frame. */
public interface TmChannelDecoder extends DataLinkComponent {

    /**
     * Decodes the bytes between {@link ByteBuffer#position()} and {@link ByteBuffer#limit()}.
     *
     * @return a buffer whose remaining bytes contain the decoded frame
     */
    ByteBuffer decode(ByteBuffer frame) throws TcTmException;

    /** Fixed encoded length, or {@code -1} when variable. */
    int encodedFrameLength();

    /** Fixed decoded length, or {@code -1} when variable. */
    int decodedFrameLength();
}
