package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataEncoder;
import org.yamcs.mdb.TcProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * A custom data encoder that converts provided binary to encoded binary in the reverse byte order.
 */
public class ReverseBinaryEncoder extends AbstractDataEncoder {

    public ReverseBinaryEncoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx) {
        // Constructor required
    }

    @Override
    public void encodeRaw(DataEncoding de, Value rawValue, BitBuffer buffer, TcProcessingContext ctx) {
        var bytes = rawValue.getBinaryValue();

        var reversedCopy = new byte[bytes.length];
        for (int i = 0, j = bytes.length - 1; i < bytes.length; i++, j--) {
            reversedCopy[i] = bytes[j];
        }

        buffer.put(reversedCopy);
    }
}
