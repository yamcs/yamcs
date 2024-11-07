package org.yamcs.mdb;

import static org.yamcs.mdb.DataEncodingUtils.*;

import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.MilStd1750A;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding;

/**
 * Decodes TM data according to the specification of the DataEncoding This is a generic catch all decoder, relies on
 * specific custom decoders implementing the DataDecoder interface when necessary.
 * 
 * @see org.yamcs.mdb.DataDecoder
 *
 */
public class DataEncodingDecoder {
    ProcessorData pdata;
    BitBuffer buffer;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingDecoder(ContainerProcessingContext pcontext) {
        this(pcontext.proccessorData, pcontext.buffer);
    }

    public DataEncodingDecoder(ProcessorData pdata, BitBuffer buffer) {
        this.pdata = pdata;
        this.buffer = buffer;
    }

    /**
     * Extracts the raw uncalibrated parameter value from the buffer.
     * 
     * @return the extracted value or null if something went wrong - in this case the parameter will be marked with
     *         aquisitionStatus = INVALID
     */
    public Value extractRaw(DataEncoding de) {
        return extractRaw(de, null);
    }

    /**
     * Extract the raw, uncalibrated parameter value from the buffer, using the provider context to find referenced
     * parameter values for variable- sized objects.
     *
     * @param de
     *            the data encoding
     * @param pcontext
     *            the processing context, or null if the context is unknown
     * @return the extracted value, or null if something went wrong - in this case the parameter will be marked with
     *         aquisitionStatus = INVALID
     */
    public Value extractRaw(DataEncoding de,
            ContainerProcessingContext pcontext) {

        if (de.getFromBinaryTransformAlgorithm() != null) { // custom algorithm
            DataDecoder dd = pdata.getDataDecoder(de);
            return dd.extractRaw(de, pcontext, buffer);
        } else {
            Value rv;
            if (de instanceof IntegerDataEncoding) {
                rv = extractRawInteger((IntegerDataEncoding) de);
            } else if (de instanceof FloatDataEncoding) {
                rv = extractRawFloat((FloatDataEncoding) de);
            } else if (de instanceof StringDataEncoding) {
                rv = extractRawString((StringDataEncoding) de, pcontext);
            } else if (de instanceof BooleanDataEncoding) {
                rv = extractRawBoolean((BooleanDataEncoding) de);
            } else if (de instanceof BinaryDataEncoding) {
                rv = extractRawBinary((BinaryDataEncoding) de, pcontext);
            } else {
                log.error("DataEncoding {} not implemented", de);
                throw new IllegalArgumentException("DataEncoding " + de + " not implemented");
            }
            return rv;
        }
    }

    private Value extractRawInteger(IntegerDataEncoding ide) {
        // Integer encoded as string, don't even try reading it as int
        if (ide.getEncoding() == Encoding.STRING) {
            return extractRaw(ide.getStringEncoding());
        }

        buffer.setByteOrder(ide.getByteOrder());
        int numBits = ide.getSizeInBits();

        long rv = buffer.getBits(numBits);
        switch (ide.getEncoding()) {
        case UNSIGNED:
            // nothing to do
            break;
        case TWOS_COMPLEMENT:
            int n = 64 - numBits;
            // shift left to get the sign and back again
            rv = (rv << n) >> n;
            break;

        case SIGN_MAGNITUDE:
            boolean negative = ((rv >>> (numBits - 1) & 1L) == 1L);

            if (negative) {
                rv = rv & ((1 << (numBits - 1)) - 1); // remove the sign bit
                rv = -rv;
            }
            break;
        case ONES_COMPLEMENT:
            negative = ((rv >>> (numBits - 1) & 1L) == 1L);
            if (negative) {
                n = 64 - numBits;
                rv = (rv << n) >> n;
                rv = ~rv;
                rv = -rv;
            }
            break;
        default: // shouldn't happen
            throw new IllegalStateException();
        }
        return getRawValue(ide, rv);
    }

    private static Value getRawValue(IntegerDataEncoding ide, long longValue) {
        if (ide.getSizeInBits() <= 32) {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                return ValueUtility.getUint32Value((int) longValue);
            } else {
                return ValueUtility.getSint32Value((int) longValue);
            }
        } else {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                return ValueUtility.getUint64Value(longValue);
            } else {
                return ValueUtility.getSint64Value(longValue);
            }
        }
    }

    private Value extractRawString(StringDataEncoding sde, ContainerProcessingContext pcontext) {
        int position = buffer.getPosition();
        if ((position & 7) != 0) {
            throw new ContainerDecodingException(pcontext,
                    "String data that does not start at byte boundary not supported");
        }

        // bmr = buffer, max, or remaining size
        int bmr = sde.getMaxSizeInBytes();
        if (bmr < 0 || bmr > buffer.remainingBytes()) {
            bmr = buffer.remainingBytes();
        }

        // first determine the buffer size
        int bufSize = -1;
        DynamicIntegerValue div = sde.getDynamicBufferSize();
        if (div != null) {
            bufSize = getDynamicSizeInBytes(div, pcontext);

            if (bufSize > bmr) {
                throw new ContainerDecodingException(pcontext,
                        "Size of string buffer computed from " + div.getDynamicInstanceRef().getName()
                                + "exceeds the "
                                + ((bmr == sde.getMaxSizeInBytes()) ? "max" : "remaining") + " size in bytes: "
                                + bufSize + ">" + bmr);
            }
            bmr = bufSize;
        } else if (sde.getSizeInBits() > 0) {
            bufSize = sde.getSizeInBits() >>> 3;
            if (bufSize > bmr) {
                throw new ContainerDecodingException(pcontext, "Fixed size of string buffer exceeds the "
                        + ((bmr == sde.getMaxSizeInBytes()) ? "max" : "remaining") + " size in bytes: "
                        + bufSize + ">" + bmr);
            }
            bmr = bufSize;
        }
        // find the string size
        int sizeInBytes = 0;
        switch (sde.getSizeType()) {
        case FIXED:
            assert (bufSize > 0);
            sizeInBytes = bufSize;
            break;
        case LEADING_SIZE:
            if (sde.getSizeInBytesOfSizeTag() > bmr) {
                throw new ContainerDecodingException(pcontext, "Size in bytes of the size tag exceeds the "
                        + (bmr == bufSize ? "buffer" : bmr == sde.getMaxSizeInBytes() ? "max" : "remaining")
                        + " size: " + sde.getSizeInBytesOfSizeTag() + ">" + bmr);
            }
            sizeInBytes = (int) buffer.getBits(sde.getSizeInBitsOfSizeTag());
            if (sde.getSizeInBytesOfSizeTag() + sizeInBytes > bmr) {
                throw new ContainerDecodingException(pcontext, "Size in bytes of buffer exceeds the "
                        + (bmr == bufSize ? "buffer" : bmr == sde.getMaxSizeInBytes() ? "max" : "remaining")
                        + " size: " + (sde.getSizeInBytesOfSizeTag() + sizeInBytes) + ">" + bmr);
            }
            if (bufSize < 0) {
                bufSize = sde.getSizeInBytesOfSizeTag() + sizeInBytes;
            }
            break;
        case TERMINATION_CHAR:
            while (sizeInBytes < bmr && buffer.getByte() != sde.getTerminationChar()) {
                sizeInBytes++;
            }
            if (bufSize < 0) {
                if (sizeInBytes == bmr) {
                    // if the buffer size is not set (either from sizeInBits or from DynamicIntegerValue)
                    // we do not want to just eat the remaining of the buffer
                    throw new ContainerDecodingException(pcontext,
                            "Cannot find string terminator 0x" + Integer.toHexString(sde.getTerminationChar()));
                }
                bufSize = sizeInBytes + 1;
            }
            buffer.setPosition(position);
            break;
        default: // shouldn't happen, CUSTOM should have an binary transform algorithm
            throw new IllegalStateException();
        }
        // extract the string
        byte[] b = new byte[sizeInBytes];
        buffer.getByteArray(b);

        buffer.setPosition(position + (bufSize << 3));
        return ValueUtility.getStringValue(new String(b, Charset.forName(sde.getEncoding())));
    }

    private Value extractRawFloat(FloatDataEncoding de) {
        switch (de.getEncoding()) {
        case IEEE754_1985:
            return extractRawIEEE754_1985(de);
        case MILSTD_1750A:
            return extractRawMILSTD_1750A(de);
        case STRING:
            return extractRaw(de.getStringDataEncoding());
        default:
            throw new IllegalArgumentException("Float Encoding " + de.getEncoding() + " not implemented");
        }
    }

    private Value extractRawIEEE754_1985(FloatDataEncoding de) {
        buffer.setByteOrder(de.getByteOrder());

        if (de.getSizeInBits() == 32) {
            return ValueUtility.getFloatValue(Float.intBitsToFloat((int) buffer.getBits(32)));
        } else {
            return ValueUtility.getDoubleValue(Double.longBitsToDouble(buffer.getBits(64)));
        }
    }

    private Value extractRawMILSTD_1750A(FloatDataEncoding de) {
        buffer.setByteOrder(de.getByteOrder());

        if (de.getSizeInBits() == 32) {
            return ValueUtility.getFloatValue((float) MilStd1750A.decode32((int) buffer.getBits(32)));
        } else {
            return ValueUtility.getDoubleValue(MilStd1750A.decode48(buffer.getBits(64)));
        }
    }

    private Value extractRawBoolean(BooleanDataEncoding bde) {
        return ValueUtility.getBooleanValue(buffer.getBits(1) != 0);
    }

    private Value extractRawBinary(BinaryDataEncoding bde,
            ContainerProcessingContext pcontext) {

        if (buffer.getPosition() % 8 != 0) {
            throw new XtceProcessingException(
                    "Binary Parameter that does not start at byte boundary not supported. bitPosition: "
                            + buffer.getPosition());
        }

        int sizeInBytes;
        switch (bde.getType()) {
        case FIXED_SIZE:
            sizeInBytes = bde.getSizeInBits() / 8;
            break;
        case LEADING_SIZE:
            sizeInBytes = (int) buffer.getBits(bde.getSizeInBitsOfSizeTag());
            break;
        case DYNAMIC:
            sizeInBytes = getDynamicSizeInBytes(bde.getDynamicSize(), pcontext);

            break;
        default: // shouldn't happen
            throw new IllegalStateException();
        }
        if (sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot extract binary parameter of size " + sizeInBytes
                    + ". Remaining in the buffer: " + buffer.remainingBytes());
        }
        byte[] b = new byte[sizeInBytes];
        buffer.getByteArray(b);
        return ValueUtility.getBinaryValue(b);
    }

    /**
     * return the nominal Value.Type of a raw value corresponding to the given XTCE data encoding definition
     * 
     * @param encoding
     * @return
     */
    public static org.yamcs.protobuf.Yamcs.Value.Type getRawType(DataEncoding encoding) {
        if (encoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding ide = (IntegerDataEncoding) encoding;
            if (ide.getSizeInBits() <= 32) {
                if (ide.getEncoding() == Encoding.UNSIGNED) {
                    return Type.UINT32;
                } else {
                    return Type.SINT32;
                }
            } else {
                if (ide.getEncoding() == Encoding.UNSIGNED) {
                    return Type.UINT64;
                } else {
                    return Type.SINT64;
                }
            }
        } else if (encoding instanceof FloatDataEncoding) {
            FloatDataEncoding fpt = (FloatDataEncoding) encoding;
            if (fpt.getSizeInBits() <= 32) {
                return Type.FLOAT;
            } else {
                return Type.DOUBLE;
            }
        } else if (encoding instanceof BooleanDataEncoding) {
            return Type.BOOLEAN;
        } else if (encoding instanceof BinaryDataEncoding) {
            return Type.BINARY;
        } else if (encoding instanceof StringDataEncoding) {
            return Type.STRING;
        } else {
            throw new IllegalStateException("Unknonw data encoding '" + encoding + "'");
        }
    }

    public static Value getRawValue(DataEncoding de, Object value) {
        if (de instanceof IntegerDataEncoding) {
            return getRawIntegerValue((IntegerDataEncoding) de, value);
        } else if (de instanceof FloatDataEncoding) {
            return getRawFloatValue((FloatDataEncoding) de, value);
        } else if (de instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(value.toString());
        } else if (de instanceof BooleanDataEncoding) {
            if (value instanceof Boolean) {
                return ValueUtility.getBooleanValue((Boolean) value);
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("Unknown data encoding '" + de + "'");
        }
    }

    private int getDynamicSizeInBytes(DynamicIntegerValue div, ContainerProcessingContext pcontext) {
        long sizeInBits;
        try {
            sizeInBits = pcontext.result.resolveDynamicIntegerValue(div);
        } catch (XtceProcessingException e) {
            throw new ContainerDecodingException(pcontext, e.getMessage());
        }

        if (sizeInBits % 8 != 0) {
            throw new ContainerDecodingException(pcontext,
                    "Variable size in bits parameter is not a multiple of 8: "
                            + sizeInBits);
        }
        if (sizeInBits > Integer.MAX_VALUE) {
            throw new ContainerDecodingException(pcontext,
                    "Variable size in bits parameter is too large: " + sizeInBits);
        }

        return (int) (sizeInBits / 8);
    }
}
