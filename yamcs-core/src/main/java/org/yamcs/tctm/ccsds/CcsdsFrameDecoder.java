package org.yamcs.tctm.ccsds;


import java.util.Arrays;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.rs.ReedSolomon;
import org.yamcs.rs.ReedSolomonException;
import org.yamcs.tctm.RawFrameDecoder;

/**
 * 
 * decodes raw frame data according to according to CCSDS 131.0-B-3.
 * <p>
 * Only Reed-Solomon and de-randomization supported.
 * 
 */
public class CcsdsFrameDecoder implements RawFrameDecoder {
    boolean derandomize;
    final ReedSolomon rs;
    int interleavingDepth;
    final int encodedFrameLength;
    final int decodedFrameLength;

    public CcsdsFrameDecoder(YConfiguration config) {
        String codec = config.getString("codec", "NONE");
        if ("RS".equalsIgnoreCase(codec)) {
            int errcc = config.getInt("errorCorrectionCapability", 16);
            if (errcc != 8 && errcc != 16) {
                throw new ConfigurationException("Bad value for errorCorrectionCapability " + errcc
                        + ". Valid values are 8 and 16");
            }
            interleavingDepth = config.getInt("interleavingDepth", 5);
            if (Arrays.binarySearch(new int[] {1,2,3,4,5,8}, interleavingDepth)<0) {
                throw new ConfigurationException("Bad value for interleavingDepth " + interleavingDepth +
                        ". Valid values are 1,2,3,4, 5 and 8");
            }

            rs = new ReedSolomon(2 * errcc, 8, 112, 11, 0x187, 0);
            encodedFrameLength = interleavingDepth * 255;
            decodedFrameLength = interleavingDepth * (255 - rs.nroots());
        } else if ("NONE".equalsIgnoreCase(codec)) {
            rs = null;
            encodedFrameLength = -1;
            decodedFrameLength = -1;
        } else {
            throw new ConfigurationException("Invlid codec '" + codec + "' specified."
                    + " Allowed are values are NONE and RS");
        }
        
        derandomize = config.getBoolean("derandomize", false);
    }

    @Override
    public int decodeFrame(byte[] data, int offset, int length) {

        if (derandomize) {
            Randomizer.randomizeTm(data, offset, length);
        }
        if (rs != null) {
            if (length != encodedFrameLength) {
                throw new IllegalArgumentException("Bad length " + length + " (expected " + encodedFrameLength + ")");
            }
            try {
                int n = rs.blockSize();
                int k = n - rs.nroots();
                for (int i = 0; i < interleavingDepth; i++) {
                    byte[] d = new byte[n];
                    for (int j = 0; j < n; j++) {
                        d[j] = data[offset + j * interleavingDepth + i];
                    }
                    rs.decode(d, null);
                    for (int j = 0; j < k; j++) {
                        data[offset + j * interleavingDepth + i] = d[j];
                    }
                }

            } catch (ReedSolomonException e) {
                return -1;
            }
            length -= rs.nroots() * interleavingDepth;
        }

        return length;
    }

    public int encodedFrameLength() {
        return encodedFrameLength;
    }

    public int decodedFrameLength() {
        return decodedFrameLength;
    }
}
