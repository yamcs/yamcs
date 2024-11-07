package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataDecoder;
import org.yamcs.mdb.ContainerProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * A custom data decoder that returns a binary value that has all the bytes reversed from the encoded binary.
 * <p>
 * This is intended to be used with a custom transformation for the {@link BinaryDataEncoding} of a binary parameter.
 * <p>
 * The implementation assumes a fixed-size encoding.
 */
public class ReverseBinaryDecoder extends AbstractDataDecoder {

    public ReverseBinaryDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx) {
        // Constructor required
    }

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer) {
        var sizeInBits = de.getSizeInBits();
        if (sizeInBits < 0) {
            throw new IllegalArgumentException("Cannot decode binary parameter. Size is not fixed");
        }

        if (sizeInBits % 8 != 0) {
            throw new IllegalArgumentException("Cannot decode binary parameter. Bit size should be multiple of 8");
        }

        var sizeInBytes = sizeInBits / 8;
        if (sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot decode binary parameter of size " + sizeInBytes
                    + ". Remaining in the buffer: " + buffer.remainingBytes());
        }

        var bytes = new byte[sizeInBytes];
        buffer.getByteArray(bytes);

        // Reverse in-place
        for (int i = 0; i < bytes.length / 2; i++) {
            var temp = bytes[i];
            bytes[i] = bytes[bytes.length - i - 1];
            bytes[bytes.length - i - 1] = temp;
        }

        return ValueUtility.getBinaryValue(bytes);
    }
}
