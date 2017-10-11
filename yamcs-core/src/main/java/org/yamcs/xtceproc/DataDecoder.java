package org.yamcs.xtceproc;

import org.yamcs.algorithms.AlgorithmExecutor;
import org.yamcs.parameter.ParameterValue;
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
    void extractRaw(DataEncoding de, BitBuffer buffer, ParameterValue pv);
}
