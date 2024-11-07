package org.yamcs.mdb;

import org.yamcs.algorithms.AlgorithmExecutor;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.DataEncoding;

/**
 * Interface to be implemented by the custom XTCE DataEncoding decoders - fromBinaryTransformAlgorithm
 * 
 */
public interface DataDecoder extends AlgorithmExecutor {
    /**
     * Extracts the raw value from the buffer. The offset inside the buffer shall be moved to the end of the parameter
     * 
     */
    Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer);

}
