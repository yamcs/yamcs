package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataDecoder;
import org.yamcs.mdb.ContainerProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * Decoder that returns a <em>boolean</em> true if the read bytes has all bits set of the provided bit mask.
 */
public class BitMaskBooleanDecoder extends AbstractDataDecoder {

    private long mask;

    public BitMaskBooleanDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, byte mask) {
        this.mask = mask;
    }

    public BitMaskBooleanDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, short mask) {
        this.mask = mask;
    }

    public BitMaskBooleanDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, int mask) {
        this.mask = mask;
    }

    public BitMaskBooleanDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, long mask) {
        this.mask = mask;
    }

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer) {
        var sizeInBits = de.getSizeInBits();
        if (sizeInBits < 0) {
            throw new IllegalArgumentException("Cannot decode boolean parameter. Size is not fixed");
        }

        if (sizeInBits % 8 != 0) {
            throw new IllegalArgumentException("Cannot decode boolean parameter. Bit size should be multiple of 8");
        }

        var sizeInBytes = sizeInBits / 8;
        if (sizeInBytes > Long.BYTES) {
            throw new IndexOutOfBoundsException("Cannot decode boolean parameter of size " + sizeInBytes
                    + ". Masking only supports up to 8 bytes");
        }

        if (sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot decode boolean parameter of size " + sizeInBytes
                    + ". Remaining in the buffer: " + buffer.remainingBytes());
        }

        var bytes = new byte[sizeInBytes];
        buffer.getByteArray(bytes);

        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) + (b & 0xFF);
        }

        var booleanValue = (mask & value) == mask;
        return ValueUtility.getBooleanValue(booleanValue);
    }
}
