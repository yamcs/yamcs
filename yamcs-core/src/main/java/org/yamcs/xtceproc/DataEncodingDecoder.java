package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
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
    ContainerProcessingContext pcontext;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingDecoder(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    /**
     *  Extracts the raw uncalibrated parameter value from the packet.
     */
    void extractRaw(DataEncoding de, ParameterValue pv) {
        if(de.getFromBinaryTransformAlgorithm()!=null) { //custom algorithm
            DataDecoder dd = pcontext.pdata.getDataDecoder(de);
            dd.extractRaw(de, pcontext.buffer, pv);
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
        
        long rv = pcontext.buffer.getBits(ide.getSizeInBits(), ide.getByteOrder(), ide.getEncoding());
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
        ContainerBuffer position = pcontext.buffer;
        ByteBuffer bb = position.bb;

        if(position.bitPosition%8!=0) {
            log.warn("Binary data that does not start at byte boundary not supported. bitPosition: {}", pcontext.buffer);
        }

        int sizeInBytes=0;
        switch(sde.getSizeType()) {
        case FIXED:
            sizeInBytes = sde.getSizeInBits()>>3;
        break;
        case LEADING_SIZE:
            for(int i=0; i<sde.getSizeInBitsOfSizeTag(); i+=8) {
                sizeInBytes = (sizeInBytes<<8) + bb.get(position.bitPosition>>3);
                position.bitPosition+=8;
            }
            break;
        case TERMINATION_CHAR:
            int byteOffset = position.bitPosition/8;
            while(bb.get(byteOffset)!=sde.getTerminationChar()){ 
                sizeInBytes++;
                byteOffset++;
            }
            break;
        }
        byte[] b=new byte[sizeInBytes];
        bb.position(position.bitPosition>>3);
        bb.get(b);
        position.bitPosition += (sizeInBytes<<3);
        if(sde.getSizeType()==SizeType.TERMINATION_CHAR) {
            position.bitPosition+=8;//the termination char
        }
        pv.setRawValue(new String(b, Charset.forName(sde.getEncoding())));
        pv.setBitSize(b.length<<3);
    }


    private void extractRawFloat(FloatDataEncoding de, ParameterValue pv) {
        ContainerBuffer position = pcontext.buffer;
        ByteBuffer bb = position.bb;

        if(position.bitPosition%8!=0) {
            log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition: {}", position.bitPosition); 
        }
        bb.order(de.getByteOrder());
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
        ContainerBuffer position = pcontext.buffer;
        ByteBuffer bb = position.bb;

        if(position.bitPosition%8!=0) {
            log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition: {}", pcontext.buffer); 
        }
        bb.order(de.getByteOrder());
        int byteOffset=position.bitPosition/8;
        position.bitPosition+=de.getSizeInBits();
        if(de.getSizeInBits()==32) {
            pv.setRawValue(bb.getFloat(byteOffset));
        } else {
            pv.setRawValue(bb.getDouble(byteOffset));
        }
    }

    private void extractRawBoolean(BooleanDataEncoding bde, ParameterValue pv) {
        ContainerBuffer position = pcontext.buffer;
        ByteBuffer bb = position.bb;

        int byteOffset = (position.bitPosition)/8;
        int bitOffsetInsideMask = position.bitPosition-8*byteOffset;
        int bitsToShift = 8-bitOffsetInsideMask-1;
        int mask = (-1<<(32-1))>>>(32-1-bitsToShift);
        int rv = bb.get(byteOffset)&0xFF;
        rv=(rv&mask)>>>bitsToShift;
        position.bitPosition+=1;
        pv.setRawValue(rv!=0);
    }

    private void extractRawBinary(BinaryDataEncoding bde, ParameterValue pv) {
        ContainerBuffer position = pcontext.buffer;
        ByteBuffer bb = position.bb;

        if(position.bitPosition%8!=0) {
            log.warn("Binary Parameter that does not start at byte boundary not supported. bitPosition: {}", pcontext.buffer);
        }

        int sizeInBytes = 0;
        switch(bde.getType()) {
        case FIXED_SIZE:
            sizeInBytes = bde.getSizeInBits()/8;
            break;
        case LEADING_SIZE:
            for(int i=0; i<bde.getSizeInBitsOfSizeTag(); i+=8) {
                sizeInBytes = (sizeInBytes<<8) + bb.get(position.bitPosition/8);
                position.bitPosition+=8;
            }
            break;
        default: //shouldn't happen
           throw new IllegalStateException();
        }
        byte[] b = new byte[sizeInBytes];
        bb.position(position.bitPosition/8);
        bb.get(b);
        pv.setRawValue(b);
        pv.setBitSize(sizeInBytes<<3);
        position.bitPosition += (sizeInBytes<<3);
    }

}
