package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.ErrorDetectionWordCalculator;

/**
 * Cylcic Redundancy Check (CRC-CCIIT 0xFFFF).
 *
 * 1 + x + x^5 + x^12 + x^16
 * 
 * Also specified in:
 * CCSDS RECOMMENDED STANDARD FOR TC SPACE DATA LINK PROTOCOL
 * CCSDS 232.0-B-3 September 2015
 * 4.1.4 FRAME ERROR CONTROL FIELD
 * 
 *
 */
public class CrcCciitCalculator implements ErrorDetectionWordCalculator {
    final int initialValue;
    final int polynomial = 0x1021; // 0001 0000 0010 0001 (0, 5, 12)
    Crc16Calculator cc = new Crc16Calculator(0x1021);

    public CrcCciitCalculator() {
        initialValue = 0xFFFF;
    }

    public CrcCciitCalculator(Map<String, Object> c) {
        initialValue = YConfiguration.getInt(c, "initialValue", 0xFFFF);
    }

    @Override
    public int compute(byte[] data, int offset, int length) {
        return cc.compute(data, offset, length, initialValue);
       
    }

}
