package org.yamcs.tctm.ccsds.error;

import org.yamcs.tctm.ErrorDetectionWordCalculator;
/**
 * CRC-32 error detection code as specified in
 * CCSDS 211.0-B-5 PROXIMITY-1 SPACE LINK PROTOCOL— DATA LINK LAYER 
 * <p>
 * also used in USLP frames   CCSDS 732.1-B-1
 * 
 * <p>
 * WARNING: we did not find an alternative implementation to compare with!
 * 
 * 
 * @author nm
 *
 */
public class ProximityCrc32 implements ErrorDetectionWordCalculator {
    final int initialValue = 0;
    final int polynomial = 0xA00805;
    Crc32Calculator cc = new Crc32Calculator(polynomial);
    
    
    @Override
    public int compute(byte[] data, int offset, int length) {
        return cc.compute(data, offset, length, initialValue);
    }


    @Override
    public int sizeInBits() {
        return 32;
    }
}
