package org.yamcs.xtceproc;

import org.yamcs.algorithms.AlgorithmExecutor;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.DataEncoding;

/**
 * Interface to be implemented by the custom XTCE DataEncoding decoders - fromBinaryTransformAlgorithm  
 * 
 * TODO: unify somehow with the algorithms in order to allow for example to use input parameters 
 * (i.e. value of other parameters may be needed in the decoding)
 *  
 *  Until then, please use the AbstractDataDecoder interface to not need to implement any of the AlgorithmExecutor
 * 
 * @author nm
 *
 */
public interface DataDecoder extends AlgorithmExecutor {
    /**
     * Extracts the raw value from the buffer.
     * The offset inside the buffer shall be moved to the end of the parameter
     * @param de
     * @param buffer
     * @return
     */
    Value extractRaw(DataEncoding de, BitBuffer buffer);
}
