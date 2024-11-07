package org.yamcs.algo;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.mdb.AbstractDataDecoder;
import org.yamcs.mdb.ContainerProcessingContext;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;

/**
 * Decoder that returns the <em>string</em> value in hex format of read bytes. This is intended to be used for special
 * use cases where the hex value represents the actual string value.
 * <p>
 * This is intended to be used with a custom transformation for the {@link BinaryDataEncoding} of a string parameter.
 * <p>
 * The implementation assumes a fixed-size encoding.
 * <p>
 * The following XTCE snippet illustrates intended usage.
 * 
 * <pre>
 * &lt;StringParameterType name="gitHash"&gt;
 *   &lt;BinaryDataEncoding&gt;
 *     &lt;SizeInBits&gt;
 *       &lt;FixedValue&gt;160&lt;/FixedValue&gt;
 *     &lt;/SizeInBits&gt;
 *     &lt;FromBinaryTransformAlgorithm name="org_yamcs_algo_HexStringDecoder"&gt;
 *       &lt;AlgorithmText language="java"&gt;org.yamcs.algo.HexStringDecoder&lt;/AlgorithmText&gt;
 *     &lt;/FromBinaryTransformAlgorithm&gt;
 *   &lt;/BinaryDataEncoding&gt;
 * &lt;/StringParameterType&gt;
 * </pre>
 */
public class HexStringDecoder extends AbstractDataDecoder {

    public HexStringDecoder(CustomAlgorithm alg, AlgorithmExecutionContext ctx) {
        // Constructor required
    }

    @Override
    public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buffer) {
        var sizeInBits = de.getSizeInBits();
        if (sizeInBits < 0) {
            throw new IllegalArgumentException("Cannot decode string parameter. Size is not fixed");
        }

        if (sizeInBits % 8 != 0) {
            throw new IllegalArgumentException("Cannot decode string parameter. Bit size should be multiple of 8");
        }

        var sizeInBytes = sizeInBits / 8;
        if (sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot decode string parameter of size " + sizeInBytes
                    + ". Remaining in the buffer: " + buffer.remainingBytes());
        }

        var barr = new byte[sizeInBytes];
        buffer.getByteArray(barr);

        var hex = StringConverter.arrayToHexString(barr).toLowerCase();
        return ValueUtility.getStringValue(hex);
    }
}
