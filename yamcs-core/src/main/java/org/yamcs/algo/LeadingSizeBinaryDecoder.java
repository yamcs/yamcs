package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataDecoder;
import org.yamcs.mdb.ContainerProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

public class LeadingSizeBinaryDecoder extends AbstractDataDecoder {

    int sizeInBitsOfSizeTag = 16;

    public LeadingSizeBinaryDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Integer sizeInBitsOfSizeTag) {
        this.sizeInBitsOfSizeTag = sizeInBitsOfSizeTag;
    }

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer) {
        int sizeInBytes = (int) buffer.getBits(sizeInBitsOfSizeTag);

        if (sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot extract binary parameter of size " + sizeInBytes
                    + ". Remaining in the buffer: " + buffer.remainingBytes());
        }
        byte[] b = new byte[sizeInBytes];
        buffer.getByteArray(b);
        return ValueUtility.getBinaryValue(b);
    }
}
