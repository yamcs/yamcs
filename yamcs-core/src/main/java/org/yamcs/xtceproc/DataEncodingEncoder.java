package org.yamcs.xtceproc;

import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.MilStd1750A;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding;

/**
 * Encodes TC data according to the DataEncoding definition
 * 
 * @author nm
 *
 */
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

        if (de instanceof IntegerDataEncoding) {
            encodeRawInteger((IntegerDataEncoding) de, rawValue);
        } else if (de instanceof FloatDataEncoding) {
            encodeRawFloat((FloatDataEncoding) de, rawValue);
        } else if (de instanceof StringDataEncoding) {
            encodeRawString((StringDataEncoding) de, rawValue);
        } else if (de instanceof BinaryDataEncoding) {
            encodeRawBinary((BinaryDataEncoding) de, rawValue);
        } else {
            log.error("DataEncoding {} not implemented", de);
            throw new IllegalArgumentException("DataEncoding " + de + " not implemented");
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
            throw new IllegalArgumentException("Cannot encode values of types " + rawValue.getType() + " to string");
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
        String v = "";
        switch (rawValue.getType()) {
        case DOUBLE:
            v = rawValue.getDoubleValue() + "";
            break;
        case FLOAT:
            v = rawValue.getFloatValue() + "";
            break;
        case UINT32:
            v = rawValue.getUint32Value() + "";
            break;
        case SINT32:
            v = rawValue.getSint32Value() + "";
            break;
        case UINT64:
            v = rawValue.getUint64Value() + "";
            break;
        case SINT64:
            v = rawValue.getSint64Value() + "";
            break;
        case STRING:
            v = rawValue.getStringValue();
            break;
        case BOOLEAN:
            v = rawValue.getBooleanValue() + "";
            break;
        case TIMESTAMP:
            v = rawValue.getTimestampValue() + "";
            break;
        case BINARY:
            v = StringConverter.arrayToHexString(rawValue.getBinaryValue());
            break;
        default:
            throw new IllegalArgumentException(
                    "String encoding for data of type " + rawValue.getType() + " not supported");
        }
        BitBuffer bitbuf = pcontext.bitbuf;

        byte[] rawValueBytes = v.getBytes(); // TBD encoding
        int initialBitPosition = bitbuf.getPosition();

        if ((initialBitPosition & 0x7) != 0) {
            throw new IllegalStateException(
                    "String Parameter that does not start at byte boundary not supported. bitPosition: "
                            + initialBitPosition);
        }

        switch (sde.getSizeType()) {
        case FIXED:
            int sdeSizeInBytes = sde.getSizeInBits() / 8;
            int sizeInBytes = (sdeSizeInBytes > rawValueBytes.length) ? rawValueBytes.length : sdeSizeInBytes;
            bitbuf.put(rawValueBytes, 0, sizeInBytes);
            if (sdeSizeInBytes > rawValueBytes.length) { // fill up with nulls to reach the required size
                byte[] nulls = new byte[sdeSizeInBytes - sizeInBytes];
                bitbuf.put(nulls);
            }
            break;
        case LEADING_SIZE:
            bitbuf.setByteOrder(ByteOrder.BIG_ENDIAN); // TBD
            bitbuf.putBits(rawValueBytes.length, sde.getSizeInBitsOfSizeTag());
            bitbuf.put(rawValueBytes);
            break;
        case TERMINATION_CHAR:
            bitbuf.put(rawValueBytes);
            bitbuf.putByte(sde.getTerminationChar());
            break;
        default:
            throw new IllegalStateException("Unsupported size type "+sde.getSizeType());
        }

        int newBitPosition = bitbuf.getPosition();
        if ((sde.getSizeInBits() != -1) && (newBitPosition - initialBitPosition < sde.getSizeInBits())) {
            bitbuf.setPosition(initialBitPosition + sde.getSizeInBits());
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
        } else if (de.getEncoding() ==  org.yamcs.xtce.FloatDataEncoding.Encoding.MILSTD_1750A) {
            if (de.getSizeInBits() == 32) {
                bitbuf.putBits(MilStd1750A.encode32(v), 32);
            } else {
                bitbuf.putBits(MilStd1750A.encode48(v), 48);
            }
        }
    }

    private void encodeRawBinary(BinaryDataEncoding bde, Value rawValue) {
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
            int bdeSizeInBytes = bde.getSizeInBits() / 8;
            int sizeInBytes = Math.min(bdeSizeInBytes, v.length);
            bitbuf.put(v, 0, sizeInBytes);
            if (bdeSizeInBytes > v.length) { // fill up with nulls to reach the required size
                byte[] nulls = new byte[bdeSizeInBytes - sizeInBytes];
                bitbuf.put(nulls);
            }
            break;
        case LEADING_SIZE:
            bitbuf.setByteOrder(ByteOrder.BIG_ENDIAN); // TBD
            bitbuf.putBits(v.length, bde.getSizeInBitsOfSizeTag()); //bde.getSizeInBitsOfSizeTag() can be 0 but bitbuf won't mind
            bitbuf.put(v);
            break;
        default:
            throw new IllegalStateException("Unsupported size type "+bde.getType());
        }
    }
}
