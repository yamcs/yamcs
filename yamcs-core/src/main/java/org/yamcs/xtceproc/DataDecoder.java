package org.yamcs.xtceproc;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.DataEncoding;

/**
 * Interface to be implemented by the custom XTCE DataEncoding decoders - fromBinaryTransformAlgorithm  
 * 
 * TODO: unify somehow with the algorithms in order to allow for example to use input parameters 
 * (i.e. value of other parameters may be needed in the decoding)
 * 
 * @author nm
 *
 */
public interface DataDecoder {
    void extractRaw(DataEncoding de, ContainerBuffer position, ParameterValue pv);
}
