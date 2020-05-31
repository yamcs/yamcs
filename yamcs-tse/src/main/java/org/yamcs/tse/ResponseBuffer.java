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

    public ResponseBuffer(String responseTermination) {
        this.responseTermination = responseTermination.getBytes();
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

            int idx = Bytes.indexOf(remainingBytes, responseTermination);
            if (idx != -1) {
                view.position(view.position() + idx + responseTermination.length);
                byte[] responseBytes = Arrays.copyOfRange(remainingBytes, 0, idx);
                return responseBytes;
            } else if (force) {
                view.position(buf.position());
                return remainingBytes; // Unterminated response
            }
        }

        return null;
    }
}
