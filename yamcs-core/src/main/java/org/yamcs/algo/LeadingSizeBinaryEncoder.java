package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataEncoder;
import org.yamcs.mdb.TcProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

public class LeadingSizeBinaryEncoder extends AbstractDataEncoder {

    int sizeInBitsOfSizeTag = 16;

    public LeadingSizeBinaryEncoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx, Integer sizeInBitsOfSizeTag) {
        this.sizeInBitsOfSizeTag = sizeInBitsOfSizeTag;
    }

    @Override
    public void encodeRaw(DataEncoding de, Value rawValue, BitBuffer bitbuf, TcProcessingContext ctx) {
        byte[] b = rawValue.getBinaryValue();
        bitbuf.putBits(b.length, sizeInBitsOfSizeTag);
        bitbuf.put(b);
    }
}
