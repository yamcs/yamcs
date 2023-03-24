package org.yamcs.tse;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.common.primitives.Bytes;

/**
 * Buffers TSE output for extracting individual "responses". A response is defined as a string that ends with a
 * configurable response termination.
 */
public class ResponseBuffer {

    private ByteBuffer buf = ByteBuffer.allocate(65536);
    private ByteBuffer view = buf.asReadOnlyBuffer();

    private byte[] responseTermination;
    private boolean fragmented;

    /**
     * @param responseTermination
     *            characters that indicate the end of a message. May be null if there is no such indication.
     * @param fragmented
     *            whether multiple responses may need to be reassembled before obtaining a complete message.
     */
    public ResponseBuffer(String responseTermination, boolean fragmented) {
        this.responseTermination = responseTermination != null ? responseTermination.getBytes() : null;
        this.fragmented = fragmented;
    }

    public void append(byte b) {
        buf.put(b);
    }

    public void append(byte[] b) {
        buf.put(b);
    }

    public void append(byte[] b, int off, int len) {
        buf.put(b, off, len);
    }

    /**
     * Reads a single 'complete' response. If no response termination is defined, this will always return null (rely on
     * timeouts).
     * 
     * @return bytes with termination stripped off.
     */
    public byte[] readSingleResponse() {
        return readSingleResponse(false);
    }

    public byte[] readSingleResponse(boolean force) {
        int remaining = buf.position() - view.position();
        if (remaining > 0) {
            view.mark();
            byte[] remainingBytes = new byte[remaining];
            view.get(remainingBytes);
            view.reset();

            if (!fragmented && responseTermination == null) {
                view.position(buf.position());
                return remainingBytes;
            }

            int idx = -1;
            if (responseTermination != null) {
                idx = Bytes.indexOf(remainingBytes, responseTermination);
            }
            if (idx != -1) {
                view.position(view.position() + idx + responseTermination.length);
                return Arrays.copyOfRange(remainingBytes, 0, idx);
            } else if (force) {
                view.position(buf.position());
                return remainingBytes; // Unterminated response
            }
        }

        return null;
    }
}
