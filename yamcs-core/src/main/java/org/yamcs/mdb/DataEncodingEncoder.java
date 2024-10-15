package org.yamcs.mdb;

import static org.yamcs.mdb.DataEncodingUtils.rawRawStringValue;

import java.io.UnsupportedEncodingException;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.MilStd1750A;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.ParameterOrArgumentRef;
import org.yamcs.xtce.StringDataEncoding;

/**
 * Encodes TC data according to the DataEncoding definition
 */
// this class needs some cleanup: normally values arriving in the encodeRaw should match the data encoding but it's not
// always the case. We should detect where the values come from and convert them there.
public class DataEncodingEncoder {
    TcProcessingContext pcontext;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingEncoder(TcProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    /**
     * Encode the raw value of the argument into the packet.
     */
    public void encodeRaw(DataEncoding de, Value rawValue) {
        pcontext.bitbuf.setByteOrder(de.getByteOrder());

        if (de.getToBinaryTransformAlgorithm() != null) {
            DataEncoder denc = pcontext.pdata.getDataEncoder(de);
            denc.encodeRaw(de, rawValue, pcontext.bitbuf, pcontext);
        } else {
            if (de instanceof IntegerDataEncoding) {
                encodeRawInteger((IntegerDataEncoding) de, rawValue);
            } else if (de instanceof FloatDataEncoding) {
                encodeRawFloat((FloatDataEncoding) de, rawValue);
            } else if (de instanceof StringDataEncoding) {
                encodeRawString((StringDataEncoding) de, rawValue);
            } else if (de instanceof BinaryDataEncoding) {
                encodeRawBinary((BinaryDataEncoding) de, rawValue, pcontext);
            } else {
                log.error("DataEncoding {} not implemented", de);
                throw new IllegalArgumentException("DataEncoding " + de + " not implemented");
            }
        }
    }

    private void encodeRawInteger(IntegerDataEncoding ide, Value rawValue) {
        // Integer encoded as string
        if (ide.getEncoding() == Encoding.STRING) {
            encodeRaw(ide.getStringEncoding(), rawValue);
            return;
        }

        // STEP 0 convert the value to a long
        long v;
        switch (rawValue.getType()) {
        case SINT32:
            v = rawValue.getSint32Value();
            break;
        case SINT64:
            v = rawValue.getSint64Value();
            break;
        case UINT32:
            v = rawValue.getUint32Value() & 0xFFFFFFFFL;
            break;
        case UINT64:
            v = rawValue.getUint64Value();
            break;
        case BOOLEAN:
            v = rawValue.getBooleanValue() ? 1 : 0;
            break;
        case DOUBLE:
            v = Math.round(rawValue.getDoubleValue());
            break;
        case FLOAT:
            v = Math.round(rawValue.getFloatValue());
            break;
        default:
            throw new IllegalArgumentException("Cannot encode values of types " + rawValue.getType() + " to integer");
        }
        int sizeInBits = ide.getSizeInBits();

        switch (ide.getEncoding()) {
        case TWOS_COMPLEMENT:
            v = (v << (64 - sizeInBits) >>> (64 - sizeInBits));
            break;
        case UNSIGNED:
            break;
        case SIGN_MAGNITUDE:
            if (v < 0) {
                v = -v;
                v &= (1 << (64 - sizeInBits));
            }
            break;
        default:
            throw new UnsupportedOperationException("encoding " + ide.getEncoding() + " not implemented");
        }
        pcontext.bitbuf.putBits(v, sizeInBits);
    }

    private void encodeRawString(StringDataEncoding sde, Value rawValue) {
        String v = rawRawStringValue(rawValue).getStringValue();
        BitBuffer bitbuf = pcontext.bitbuf;

        int initialBitPosition = bitbuf.getPosition();

        if ((initialBitPosition & 0x7) != 0) {
            throw new IllegalStateException(
                    "String Parameter that does not start at byte boundary not supported. bitPosition: "
                            + initialBitPosition);
        }

        byte[] rawValueBytes;
        try {
            rawValueBytes = v.getBytes(sde.getEncoding());
        } catch (UnsupportedEncodingException e1) {
            throw new CommandEncodingException(pcontext, "Unsupported encoding '" + sde.getEncoding() + "'");
        }

        // bmr = buffer, max, or remaining size
        int bmr = sde.getMaxSizeInBytes();
        if (bmr < 0 || bmr > bitbuf.remainingBytes()) {
            bmr = bitbuf.remainingBytes();
        }

        // first determine the buffer size
        int bufSize = -1;
        DynamicIntegerValue div = sde.getDynamicBufferSize();
        if (div != null) {
            long sizeInBits;
            try {
                sizeInBits = pcontext.resolveDynamicIntegerValue(div);
            } catch (XtceProcessingException e) {
                throw new CommandEncodingException(pcontext, e.getMessage());
            }
            if ((sizeInBits & 7) != 0) {
                throw new CommandEncodingException(pcontext,
                        "Size of string buffer (computed from " + div.getDynamicInstanceRef().getName() + ")"
                                + " is not a multiple of 8: " + sizeInBits);
            }
            bufSize = (int) (sizeInBits >>> 3);
            if (bufSize > bmr) {
                throw new CommandEncodingException(pcontext,
                        "Size of string buffer (computed from " + div.getDynamicInstanceRef().getName() + ")"
                                + " exceeds the "
                                + ((bmr == sde.getMaxSizeInBytes()) ? "max" : "remaining") + " size: "
                                + sizeInBits + ">" + (8 * bmr));
            }
            bmr = bufSize;
        } else if (sde.getSizeInBits() > 0) {
            bufSize = sde.getSizeInBits() >>> 3;
            if (bufSize > bmr) {
                throw new CommandEncodingException(pcontext, "Fixed size of string buffer exceeds the "
                        + ((bmr == sde.getMaxSizeInBytes()) ? "max" : "remaining") + " size: "
                        + sde.getSizeInBits() + ">" + (8 * bmr));
            }
            bmr = bufSize;
        }

        // then write the string
        int sizeInBytes;
        switch (sde.getSizeType()) {
        case FIXED:
            assert (bufSize > 0);
            sizeInBytes = rawValueBytes.length;
            if (sizeInBytes > bmr) {
                throw new CommandEncodingException(pcontext, "String size is greater that "
                        + (bmr == bufSize ? "buffer" : bmr == sde.getMaxSizeInBytes() ? "max" : "remaining")
                        + " size: " + sizeInBytes + ">" + bmr);
            }
            bitbuf.put(rawValueBytes);
            break;
        case LEADING_SIZE:
            sizeInBytes = rawValueBytes.length + sde.getSizeInBytesOfSizeTag();
            if (sizeInBytes > bmr) {
                throw new CommandEncodingException(pcontext, "String size + tag is greater that "
                        + (bmr == bufSize ? "buffer" : bmr == sde.getMaxSizeInBytes() ? "max" : "remaining")
                        + " size: " + sizeInBytes + ">" + bmr);
            }
            bitbuf.setByteOrder(sde.getByteOrder());
            bitbuf.putBits(sizeInBytes - sde.getSizeInBytesOfSizeTag(), sde.getSizeInBitsOfSizeTag());
            bitbuf.put(rawValueBytes);
            break;
        case TERMINATION_CHAR:
            sizeInBytes = rawValueBytes.length;
            if (bufSize < 0) {
                sizeInBytes++;
            }
            if (sizeInBytes > bmr) {
                throw new CommandEncodingException(pcontext, "String size "
                        + (bufSize < 0 ? "with terminator " : "")
                        + " is greater than "
                        + (bmr == bufSize ? "buffer" : bmr == sde.getMaxSizeInBytes() ? "max" : "remaining")
                        + " size: " + sizeInBytes + ">" + bmr);
            }

            bitbuf.put(rawValueBytes);
            if (bufSize < 0) {
                bitbuf.putByte(sde.getTerminationChar());
            } else if (sizeInBytes < bufSize) {
                bitbuf.putByte(sde.getTerminationChar());
                sizeInBytes++;
            }
            break;
        default:
            throw new IllegalStateException("Unsupported size type " + sde.getSizeType());
        }

        if (bufSize > sizeInBytes) { // fill up with nulls to reach the required size
            byte[] nulls = new byte[bufSize - sizeInBytes];
            bitbuf.put(nulls);
        }
    }

    private void encodeRawFloat(FloatDataEncoding de, Value rawValue) {
        switch (de.getEncoding()) {
        case IEEE754_1985:
        case MILSTD_1750A:
            encodeRawFloat2(de, rawValue);
            break;
        case STRING:
            encodeRaw(de.getStringDataEncoding(), rawValue);
            break;
        default:
            throw new IllegalArgumentException("Float Encoding " + de.getEncoding() + " not implemented");
        }
    }

    private void encodeRawFloat2(FloatDataEncoding de, Value rawValue) {
        double v;
        switch (rawValue.getType()) {
        case DOUBLE:
            v = rawValue.getDoubleValue();
            break;
        case FLOAT:
            v = rawValue.getFloatValue();
            break;
        case UINT32:
            v = rawValue.getUint32Value();
            break;
        case SINT32:
            v = rawValue.getSint32Value();
            break;
        case UINT64:
            v = rawValue.getUint64Value();
            break;
        case SINT64:
            v = rawValue.getSint64Value();
            break;
        default:
            throw new IllegalArgumentException(
                    "Float encoding for data of type " + rawValue.getType() + " not supported");
        }

        BitBuffer bitbuf = pcontext.bitbuf;
        bitbuf.setByteOrder(de.getByteOrder());
        if (de.getEncoding() == org.yamcs.xtce.FloatDataEncoding.Encoding.IEEE754_1985) {
            if (de.getSizeInBits() == 32) {
                bitbuf.putBits(Float.floatToIntBits((float) v), 32);
            } else {
                bitbuf.putBits(Double.doubleToLongBits(v), 64);
            }
        } else if (de.getEncoding() == org.yamcs.xtce.FloatDataEncoding.Encoding.MILSTD_1750A) {
            if (de.getSizeInBits() == 32) {
                bitbuf.putBits(MilStd1750A.encode32(v), 32);
            } else {
                bitbuf.putBits(MilStd1750A.encode48(v), 48);
            }
        }
    }

    private void encodeRawBinary(BinaryDataEncoding bde, Value rawValue, TcProcessingContext pcontext) {
        byte[] v;
        if (rawValue.getType() == Type.BINARY) {
            v = rawValue.getBinaryValue();
        } else if (rawValue.getType() == Type.STRING) {
            v = rawValue.getStringValue().getBytes(); // TBD encoding
        } else {
            throw new IllegalArgumentException("Cannot encode as binary data values of type " + rawValue.getType());
        }
        BitBuffer bitbuf = pcontext.bitbuf;
        switch (bde.getType()) {
        case FIXED_SIZE:
            int sizeInBytes, bdeSizeInBytes;
            if (bde.getSizeInBits() < 0) { // if the size is negative, we take all the data
                bdeSizeInBytes = sizeInBytes = v.length;
            } else {
                bdeSizeInBytes = bde.getSizeInBits() / 8;
                sizeInBytes = Math.min(bdeSizeInBytes, v.length);
            }
            bitbuf.put(v, 0, sizeInBytes);
            if (bdeSizeInBytes > v.length) { // fill up with nulls to reach the required size
                byte[] nulls = new byte[bdeSizeInBytes - sizeInBytes];
                bitbuf.put(nulls);
            }
            break;
        case LEADING_SIZE:
            bitbuf.setByteOrder(ByteOrder.BIG_ENDIAN); // TBD
            bitbuf.putBits(v.length, bde.getSizeInBitsOfSizeTag()); // bde.getSizeInBitsOfSizeTag() can be 0 but bitbuf
                                                                    // won't mind
            bitbuf.put(v);
            break;
        case DYNAMIC:
            DynamicIntegerValue div = bde.getDynamicSize();
            ParameterOrArgumentRef ref = div.getDynamicInstanceRef();
            String sizeName = ref.getName();
            ArgumentValue sizeArgValue = pcontext.getArgumentValue(sizeName);
            if (sizeArgValue == null) {
                throw new IllegalStateException(
                        "No argument supplied for binary variable size: " + sizeName);
            }
            Value sizeValue = ref.useCalibratedValue() ? sizeArgValue.getEngValue() : sizeArgValue.getRawValue();
            long sizeInBits;
            try {
                sizeInBits = div.transform(sizeValue.toLong());
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException(
                        "Cannot convert argument " + sizeName + " of type " + sizeValue.getClass() + " to integer");
            }
            if (sizeInBits % 8 != 0) {
                throw new CommandEncodingException(
                        "Binary variable size argument is not a multiple of 8: " + sizeInBits);
            }
            if (sizeInBits < 0) {
                throw new CommandEncodingException(
                        "Binary variable size argument is negative: " + sizeInBits);
            }
            int dynSizeInBytes = (int) (sizeInBits / 8);
            int dataSizeInBytes = Math.min(dynSizeInBytes, v.length);
            bitbuf.put(v, 0, dataSizeInBytes);
            if (dynSizeInBytes > v.length) {
                // Fill with nulls to reach the required size.
                byte[] nuls = new byte[dynSizeInBytes - dataSizeInBytes];
                bitbuf.put(nuls);
            }
            break;
        default:
            throw new IllegalStateException("Unsupported size type " + bde.getType());
        }
    }
}
