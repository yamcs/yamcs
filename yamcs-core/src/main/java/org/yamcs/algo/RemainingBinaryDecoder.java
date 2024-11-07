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
 * A decoder that returns a binary value containing all of the remaining bytes.
 * <p>
 * An example where this may be useful is a packet that contains an arbitrarily sized blob of data with no length
 * indication.
 * <p>
 * The following XTCE snippet illustrates intended usage.
 * 
 * <pre>
 * &lt;BinaryParameterType name="outputData"&gt;
 *   &lt;BinaryDataEncoding&gt;
 *     &lt;SizeInBits&gt;
 *       &lt;DynamicValue&gt;
 *         &lt;ParameterInstanceRef parameterRef="_yamcs_ignore"/&gt;
 *       &lt;/DynamicValue&gt;
 *     &lt;/SizeInBits&gt;
 *     &lt;FromBinaryTransformAlgorithm name="org_yamcs_algo_RemainingBinaryDecoder"&gt;
 *       &lt;AlgorithmText language="java"&gt;org.yamcs.algo.RemainingBinaryDecoder&lt;/AlgorithmText&gt;
 *     &lt;/FromBinaryTransformAlgorithm&gt;
 *   &lt;/BinaryDataEncoding&gt;
 * &lt;/BinaryParameterType&gt;
 * </pre>
 */
public class RemainingBinaryDecoder extends AbstractDataDecoder {

    public RemainingBinaryDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx) {
        // Constructor required
    }

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer) {
        var b = new byte[buffer.remainingBytes()];
        buffer.getByteArray(b);
        return ValueUtility.getBinaryValue(b);
    }
}
