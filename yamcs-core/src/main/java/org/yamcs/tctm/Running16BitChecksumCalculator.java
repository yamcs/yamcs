package org.yamcs.tctm;

import java.nio.ByteBuffer;

public class Running16BitChecksumCalculator implements ErrorDetectionWordCalculator {

    @Override
    public int compute(byte[] data, int offset, int length) {
        if((length&1) != 0) {
            throw new IllegalArgumentException("Cannot compute checksum on a odd number of bytes");
        }
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        int checksum = 0;
        while(bb.hasRemaining()) {
            checksum+=bb.getShort();
        }
        return checksum & 0xFFFF;
    }

    @Override
    public int sizeInBits() {
        return 16;
    }
}
