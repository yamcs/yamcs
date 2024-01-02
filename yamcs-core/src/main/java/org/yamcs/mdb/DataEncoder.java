package org.yamcs.mdb;

import org.yamcs.algorithms.AlgorithmExecutor;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.DataEncoding;

/**
 * Interface to be implemented by the custom XTCE DataEncoding decoders - toBinaryTransformAlgorithm
 * 
 * @author nm
 *
 */
public interface DataEncoder extends AlgorithmExecutor {
    /**
     * Encode the raw value into the buffer.
     * <p>
     * The offset inside the buffer shall be moved to the end of the parameter
     *
     * @param de
     *            the data encoding definition of which this algorithm is part of
     * @param rawValue
     *            raw value to be encoded
     * @param buffer
     *            buffer in which the value should be encoded
     * @param ctx
     *            TC processing context
     */
    void encodeRaw(DataEncoding de, Value rawValue, BitBuffer buffer, TcProcessingContext ctx);
}
