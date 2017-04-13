package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingPosition;

/**
 * Decodes TM data according to the specification of the DataEncoding
 * @author nm
 *
 */
public class DataEncodingDecoder {
    ContainerProcessingContext pcontext;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingDecoder(ContainerProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    /**
     *  Extracts the raw uncalibrated parameter value from the packet.
     */
    void extractRaw(DataEncoding de, ParameterValue pv){
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

    private void extractRawInteger(IntegerDataEncoding ide, ParameterValue pv) {
        ContainerProcessingPosition position = pcontext.position;
        ByteBuffer bb = position.bb;
        // Integer encoded as string, don't even try reading it as int
        if(ide.getEncoding() == Encoding.string) {
            extractRaw(ide.getStringEncoding(), pv);
            return;
        }
        
        int byteOffset = (position.bitPosition)/8;
        int byteSize = (position.bitPosition + ide.getSizeInBits()-1)/8-byteOffset+1;
        int bitOffsetInsideMask = position.bitPosition - 8*byteOffset;
        int bitsToShift = 8*byteSize-bitOffsetInsideMask-ide.getSizeInBits();
        long mask = (-1L<<(64-ide.getSizeInBits()))>>>(64-ide.getSizeInBits()-bitsToShift);
        bb.order(ide.getByteOrder());

        long rv=0;
        switch(byteSize) {
        case 1:
            rv = bb.get(byteOffset);
            break;
        case 2:
            rv = bb.getShort(byteOffset);
            break;
        case 3:
            if(ide.getByteOrder()==ByteOrder.BIG_ENDIAN) {
                rv = (bb.getShort(byteOffset) & 0xFFFF) << 8;
                rv += bb.get(byteOffset + 2) & 0xFF;
            } else {
                rv = bb.getShort(byteOffset) & 0xFFFF;
                rv += (bb.get(byteOffset + 2) & 0xFF) << 16;
            }
            break;
        case 4:
            rv = bb.getInt(byteOffset);
            break;
        case 5:
            if(ide.getByteOrder()==ByteOrder.BIG_ENDIAN) {
                rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 8;
                rv += bb.get(byteOffset + 4) & 0xFF;
            } else {
                rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                rv += (bb.get(byteOffset + 4) & 0xFFL) << 32;
            }
            break;
        case 6:
            if (ide.getByteOrder()==ByteOrder.BIG_ENDIAN) {
                rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 16;
                rv += bb.getShort(byteOffset + 4) & 0xFFFF;
            } else {
                rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                rv += bb.getShort(byteOffset + 4) & 0xFFFFL << 32;
            }
            break;
        case 7:
            if (ide.getByteOrder() == ByteOrder.BIG_ENDIAN) {
                rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 24;
                rv += (bb.getShort(byteOffset + 4) & 0xFFFFL) << 8;
                rv += bb.get(byteOffset + 6) & 0xFFL;
            } else {
                rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                rv += (bb.getShort(byteOffset + 4) & 0xFFFFL) << 32;
                rv += (bb.get(byteOffset + 6) & 0xFFL) << 48;
            }
            break;
        case 8:
            rv = bb.getLong(byteOffset);
            break;
        default:
        	log.warn(String.format("parameter extraction for %d bytes not supported, used for %s", byteSize, pv.getParameter()));
        }
        position.bitPosition+=ide.getSizeInBits();
        switch(ide.getEncoding()) {
        case twosComplement:
            //we shift it to the left first such that the sign bit arrives on the first position
            rv= (rv&mask)<<(64-ide.getSizeInBits()-bitsToShift);
            rv=rv>>(64-ide.getSizeInBits());
            if (ide.getSizeInBits() <= 32)
                pv.setRawSignedInteger((int)rv);
            else
                pv.setRawSignedLong(rv);
            break;
        case unsigned:
            //we use the ">>>" such that the sign bit is not carried
            rv=(rv&mask)>>>bitsToShift;
            break;
        case signMagnitude:
        	boolean negative = ((rv>>>(ide.getSizeInBits()-1) & 1L) == 1L);
        	mask >>>= 1; // Don't include sign in mask
            rv=(rv&(mask))>>>bitsToShift;
            if (negative) {
                rv = -rv;
            }
            break;
        default:
            throw new UnsupportedOperationException("encoding "+ide.getEncoding()+" not implemented");
        }
        setRawValue(ide, pv, rv);
    }
    
    private static void setRawValue(IntegerDataEncoding ide, ParameterValue pv, long longValue) {
        if(ide.getSizeInBits() <= 32) {
            if(ide.getEncoding() == Encoding.unsigned) {
                pv.setRawUnsignedInteger((int)longValue);
            } else {
                pv.setRawSignedInteger((int)longValue);
            }
        } else {
            if(ide.getEncoding() == Encoding.unsigned) {
                pv.setRawUnsignedLong(longValue);
            } else {
                pv.setRawSignedLong(longValue);
            }
        }
    }

    private void extractRawString(StringDataEncoding sde, ParameterValue pv) {
        ContainerProcessingPosition position = pcontext.position;
        ByteBuffer bb = position.bb;
       
        if(position.bitPosition%8!=0) {
            log.warn("String Parameter that does not start at byte boundary not supported. bitPosition: {}", pcontext.position);
        }
        int sizeInBytes=0;
        switch(sde.getSizeType()) {
        case Fixed:
            sizeInBytes=sde.getSizeInBits()/8;
            break;
        case LeadingSize:
            for(int i=0;i<sde.getSizeInBitsOfSizeTag();i+=8) {
                sizeInBytes=(sizeInBytes<<8) + bb.get(position.bitPosition/8);
                position.bitPosition+=8;
            }
            break;
        case TerminationChar:
            int byteOffset = position.bitPosition/8;
            while(bb.get(byteOffset)!=sde.getTerminationChar()){ 
                sizeInBytes++;
                byteOffset++;
            }
            break;
        }
        byte[] b=new byte[sizeInBytes];
        bb.position(position.bitPosition/8);
        bb.get(b);
        pv.setRawValue(new String(b));
        pv.setBitSize(8*sizeInBytes);
        position.bitPosition += 8*sizeInBytes;
        if(sde.getSizeType()==SizeType.TerminationChar) {
            position.bitPosition+=8;//the termination char
        }
    }

    private void extractRawFloat(FloatDataEncoding de, ParameterValue pv) {
        ContainerProcessingPosition position = pcontext.position;
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
        ContainerProcessingPosition position = pcontext.position;
        ByteBuffer bb = position.bb;
       
        if(position.bitPosition%8!=0) {
            log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition: {}", pcontext.position); 
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
        ContainerProcessingPosition position = pcontext.position;
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
        ContainerProcessingPosition position = pcontext.position;
        ByteBuffer bb = position.bb;
       
        if(position.bitPosition%8!=0) {
            log.warn("Binary Parameter that does not start at byte boundary not supported. bitPosition: {}", pcontext.position);
        }
        byte[] b = new byte[bde.getSizeInBits()/8];
        bb.position(position.bitPosition/8);
        bb.get(b);
        pv.setRawValue(b);
        position.bitPosition += bde.getSizeInBits();
    }
}
