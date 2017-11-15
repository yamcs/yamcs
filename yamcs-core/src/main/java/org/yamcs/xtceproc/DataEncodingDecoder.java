package org.yamcs.xtceproc;

import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;

/**
 * Decodes TM data according to the specification of the DataEncoding
 * This is a generic catch all decoder, relies on specific custom decoders implementing
 * the DataDecoder interface when necessary.
 * @see  org.yamcs.xtceproc.DataDecoder
 *
 * @author nm
 *
 */
public class DataEncodingDecoder {
    ProcessorData pdata;
    BitBuffer buffer;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingDecoder(ContainerProcessingContext pcontext) {
        this(pcontext.pdata, pcontext.buffer);
    }

    public DataEncodingDecoder(ProcessorData pdata, BitBuffer buffer) {
        this.pdata = pdata;
        this.buffer = buffer;
    }

    /**
     *  Extracts the raw uncalibrated parameter value from the buffer.
     */
    public void extractRaw(DataEncoding de, ParameterValue pv) {
        if(de.getFromBinaryTransformAlgorithm()!=null) { //custom algorithm
            DataDecoder dd = pdata.getDataDecoder(de);
            dd.extractRaw(de, buffer, pv);
        } else {
            if(de instanceof IntegerDataEncoding) {
                extractRawInteger((IntegerDataEncoding) de, pv);
            } else if(de instanceof FloatDataEncoding) {
                extractRawFloat((FloatDataEncoding) de, pv);
            } else if(de instanceof StringDataEncoding) {
                extractRawString((StringDataEncoding) de, pv);
            } else if(de instanceof BooleanDataEncoding) {
                extractRawBoolean((BooleanDataEncoding) de, pv);
            } else if(de instanceof BinaryDataEncoding) {
                extractRawBinary((BinaryDataEncoding) de, pv);
            } else {
                log.error("DataEncoding {} not implemented", de);
                throw new IllegalArgumentException("DataEncoding "+de+" not implemented");
            }
        }
    }

    private void extractRawInteger(IntegerDataEncoding ide, ParameterValue pv) {
        // Integer encoded as string, don't even try reading it as int
        if(ide.getEncoding() == Encoding.STRING) {
            extractRaw(ide.getStringEncoding(), pv);
            return;
        }

        buffer.setByteOrder(ide.getByteOrder());
        int numBits = ide.getSizeInBits();

        long rv = buffer.getBits(numBits);
        switch(ide.getEncoding()) {
        case UNSIGNED:
            //nothing to do
            break;
        case TWOS_COMPLEMENT:
            int n = 64-numBits;
            //shift left to get the sign and back again
            rv = (rv <<n)>>n;
            break;

            case SIGN_MAGNITUDE:
                boolean negative = ((rv>>>(numBits-1) & 1L) == 1L);

                if (negative) {
                    rv = rv&((1<<(numBits-1))-1); //remove the sign bit
                    rv = -rv;
                }
                break;
            case ONES_COMPLEMENT:
                negative = ((rv>>>(numBits-1) & 1L) == 1L);
                if (negative) {
                    n = 64-numBits;
                    rv = (rv <<n)>>n;
                    rv = ~rv;
                    rv = -rv;

                }
                break;
        }
        setRawValue(ide, pv, rv);
    }

    private static void setRawValue(IntegerDataEncoding ide, ParameterValue pv, long longValue) {
        if(ide.getSizeInBits() <= 32) {
            if(ide.getEncoding() == Encoding.UNSIGNED) {
                pv.setRawUnsignedInteger((int)longValue);
            } else {
                pv.setRawSignedInteger((int)longValue);
            }
        } else {
            if(ide.getEncoding() == Encoding.UNSIGNED) {
                pv.setRawUnsignedLong(longValue);
            } else {
                pv.setRawSignedLong(longValue);
            }
        }
    }

    private void extractRawString(StringDataEncoding sde, ParameterValue pv) {
        if(buffer.getPosition()%8!=0) {
            log.warn("Binary data that does not start at byte boundary not supported. bitPosition: {}", buffer);
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            return;
        }

        int sizeInBytes=0;
        switch(sde.getSizeType()) {
        case FIXED:
            sizeInBytes = sde.getSizeInBits()>>3;
        break;
        case LEADING_SIZE:
            sizeInBytes = (int) buffer.getBits(sde.getSizeInBitsOfSizeTag());
            break;
        case TERMINATION_CHAR:
            int p = buffer.getPosition();
            while(buffer.getByte()!=sde.getTerminationChar()){
                sizeInBytes++;
            }
            buffer.setPosition(p);
            break;
        }
        byte[] b = new byte[sizeInBytes];
        buffer.getByteArray(b);

        if(sde.getSizeType()==SizeType.TERMINATION_CHAR) {
            buffer.getByte();//the termination char
        }
        pv.setRawValue(new String(b, Charset.forName(sde.getEncoding())));
        pv.setBitSize(b.length<<3);
    }


    private void extractRawFloat(FloatDataEncoding de, ParameterValue pv) {
        switch(de.getEncoding()) {
        case IEEE754_1985:
            extractRawIEEE754_1985(de, pv);
            break;
        case STRING:
            extractRaw(de.getStringDataEncoding(), pv);
            break;
        default:
            throw new IllegalArgumentException("Float Encoding "+de.getEncoding()+" not implemented");
        }
    }

    private void extractRawIEEE754_1985(FloatDataEncoding de, ParameterValue pv) {
        buffer.setByteOrder(de.getByteOrder());

        if(de.getSizeInBits()==32) {
            pv.setRawFloatValue(Float.intBitsToFloat((int) buffer.getBits(32)));
        } else {
            pv.setRawDoubleValue(Double.longBitsToDouble(buffer.getBits(64)));
        }
    }

    private void extractRawBoolean(BooleanDataEncoding bde, ParameterValue pv) {
        pv.setRawValue(buffer.getBits(1) != 0);
    }

    private void extractRawBinary(BinaryDataEncoding bde, ParameterValue pv) {
        if(buffer.getPosition() % 8 != 0) {
            log.warn("Binary Parameter that does not start at byte boundary not supported. bitPosition: {}", buffer);
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            return;
        }

        int sizeInBytes;
        switch(bde.getType()) {
        case FIXED_SIZE:
            sizeInBytes = bde.getSizeInBits()/8;
            break;
        case LEADING_SIZE:
            sizeInBytes = (int) buffer.getBits(bde.getSizeInBitsOfSizeTag());
            break;
        default: //shouldn't happen
            throw new IllegalStateException();
        }
        if(sizeInBytes > buffer.remainingBytes()) {
            throw new IndexOutOfBoundsException("Cannot extract binary parameter of size "+sizeInBytes+". Remaining in the buffer: "+buffer.remainingBytes());
        }
        byte[] b = new byte[sizeInBytes];
        buffer.getByteArray(b);
        pv.setRawValue(b);
        pv.setBitSize(sizeInBytes<<3);
    }
}
