package org.yamcs.tctm.ccsds;


import java.util.Arrays;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.rs.ReedSolomon;
import org.yamcs.tctm.RawFrameEnDec;

/**
 * 
 * decodes raw frame data according to according to CCSDS 131.0-B-3.
 * Only Reed-Solomon and de-randomization supported.
 * 
 */
public class CcsdsFrameEncoder implements RawFrameEnDec {
    boolean randomize;
    final ReedSolomon rs;
    int interleavingDepth;
    final int encodedFrameLength;
    final int decodedFrameLength;

    public CcsdsFrameEncoder(YConfiguration config, int maxFrameLength) {
        String codec = config.getString("codec", "NONE");
        if ("RS".equalsIgnoreCase(codec)) {
            int errcc = config.getInt("errorCorrectionCapability", 16);
            if (errcc != 8 && errcc != 16) {
                throw new ConfigurationException("Bad value for errorCorrectionCapability " + interleavingDepth
                        + ". Valid values are 8 and 16");
            }
            interleavingDepth = config.getInt("interleavingDepth", 5);
            if (Arrays.binarySearch(new int[] {1,2,3,4,5,8}, interleavingDepth)<0) {
                throw new ConfigurationException("Bad value for interleavingDepth " + interleavingDepth +
                        ". Valid values are 1,2,3,4, 5 and 8");
            }

            rs = new ReedSolomon(2 * errcc, 8, 112, 11, 0x187, 0);
            encodedFrameLength = maxFrameLength + rs.nroots() * interleavingDepth;
            decodedFrameLength = maxFrameLength;
        } else if ("NONE".equalsIgnoreCase(codec)) {
            rs = null;
            encodedFrameLength = -1;
            decodedFrameLength = -1;
        } else {
            throw new ConfigurationException("Invlid codec '" + codec + "' specified."
                    + " Allowed are values are NONE and RS");
        }
        
        randomize = config.getBoolean("randomize", false);
    }

    @Override
    public int decodeFrame(byte[] data, int offset, int length) {
        throw new UnsupportedOperationException("Unimplemented method 'encodeFrame'");
    }

    public int encodedFrameLength() {
        return encodedFrameLength;
    }

    public int decodedFrameLength() {
        return decodedFrameLength;
    }

    @Override
    public int encodeFrame(byte[] data, int offset, int length) {
        if (rs != null) {
            if (length != decodedFrameLength) {
                throw new IllegalArgumentException("Bad length " + length + " (expected " + decodedFrameLength + ")");
            }
    
            byte[] parity = new byte[rs.nroots()];
            byte[] interleavedData = new byte[encodedFrameLength];
        
            // Process each interleaving depth
            for (int i = 0; i < interleavingDepth; i++) {
                // Copy the interleaved data from the original buffer
                for (int j = 0; j < length / interleavingDepth; j++) {
                    interleavedData[j] = data[offset + i + j * interleavingDepth];
                }
            }

            // Encode each interleaved block using Reed-Solomon
            int blockLength = length / interleavingDepth;
            for (int i = 0; i < interleavingDepth; i++) {
                byte[] block = Arrays.copyOfRange(interleavedData, i * blockLength, (i + 1) * blockLength);
                rs.encode(block, parity);

                // Place the encoded data and parity back into the data array
                for (int j = 0; j < blockLength; j++) {
                    data[offset + i + j * interleavingDepth] = block[j];
                }
                for (int j = 0; j < rs.nroots(); j++) {
                    data[offset + i + (blockLength + j) * interleavingDepth] = parity[j];
                }
            }
        }
    
        if (randomize) {
            Randomizer.randomizeTm(data, 0, encodedFrameLength);
        }
        
        return encodedFrameLength;
    }
}

