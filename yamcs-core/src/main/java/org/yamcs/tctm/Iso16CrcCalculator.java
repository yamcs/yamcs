package org.yamcs.tctm;

/**
 * ISO CRC calculator as described in ECSS-E-ST-70-41C 15 April 2016, appendix B.2
 * <p>
 * It has been devised by <a href="https://en.wikipedia.org/wiki/Fletcher%27s_checksum">John G. Fletcher</a>. 
 *
 * @author nm
 *
 */
public class Iso16CrcCalculator implements ErrorDetectionWordCalculator {

    @Override
    public int compute(byte[] data, int offset, int length) {
        long c0 = 0;
        long c1 = 0;
        int i = offset;
        while (i < length) {
            //compute in blocks to not overflow c1
            int n = length;
            if (n - i > 268961283) {
                n -= 268961283;
            }

            for (; i < n; i++) {
                c0 = c0 + (data[i] & 0xFF);
                c1 = c1 + c0;
            }
            c0 = c0 % 255;
            c1 = c1 % 255;
        }

        long ck1 = ~((c0 + c1) % 255);
        long ck2 = c1;
        if (ck1 == 0) {
            ck1 = 255;
        }
        if (ck2 == 0) {
            ck2 = 255;
        }
        return (int) (((ck1 & 0xFF) << 8) + (ck2 & 0xFF));
    }


    @Override
    public int sizeInBits() {
        return 16;
    }

}
