package org.yamcs.tctm.ccsds.error;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.ErrorDetectionWordCalculator;

public class CrcMcrf4xxCalculator implements ErrorDetectionWordCalculator {
    final int initialValue;

    private final Crc16McrfWord crc;

    public CrcMcrf4xxCalculator() {
        initialValue = 0xFFFF;
        crc = new Crc16McrfWord(initialValue);
    }

    public CrcMcrf4xxCalculator(YConfiguration c) {
        initialValue = c.getInt("initialValue", 0xFFFF);
        crc = new Crc16McrfWord(initialValue);
    }

    @Override
    public int compute(byte[] data, int offset, int length) {
        crc.start_checksum();

        for (int i = offset; i < offset + length; i++) {
            crc.update_checksum(data[i]);
        }

        return crc.getCurCrcValue();
    }

    @Override
    public int sizeInBits() {
        return 16;
    }
}