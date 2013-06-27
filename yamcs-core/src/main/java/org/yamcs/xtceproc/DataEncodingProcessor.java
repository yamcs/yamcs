package org.yamcs.xtceproc;

import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;

public class DataEncodingProcessor {
    ProcessingContext pcontext;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    /**
     *  Extracts the parameter from the packet.
     * @param bitb
     * @return value of the parameter after extraction
     */
    Object extractRawAndCalibrate(DataEncoding de, ParameterValue pv){
        if(de instanceof IntegerDataEncoding) {
            return extractRawAndCalibrateInteger((IntegerDataEncoding) de, pv);
        } else if(de instanceof FloatDataEncoding) {
            return extractRawAndCalibrateFloat((FloatDataEncoding) de, pv);
        } else if(de instanceof StringDataEncoding) {
            return extractRawAndCalibrateString((StringDataEncoding) de, pv);
        } else if(de instanceof BinaryDataEncoding) {
            return extractRawAndCalibrateBinary((BinaryDataEncoding) de, pv);
        }

        log.error("DataEncoding "+de+" not implemented");
        throw new RuntimeException("DataEncoding "+de+" not implemented");
    }

    private Object extractRawAndCalibrateInteger(IntegerDataEncoding ide, ParameterValue pv) {
        int byteOffset=(pcontext.bitPosition)/8;
        int byteSize=(pcontext.bitPosition+ide.getSizeInBits()-1)/8-byteOffset+1;
        int bitOffsetInsideMask=pcontext.bitPosition-8*byteOffset;
        int bitsToShift=8*byteSize-bitOffsetInsideMask-ide.getSizeInBits();
        int mask=(-1<<(32-ide.getSizeInBits()))>>>(32-ide.getSizeInBits()-bitsToShift);

        pcontext.bb.order(ide.getByteOrder());

        long rv=0;
        switch(byteSize) {
        case 1:
            rv=pcontext.bb.get(byteOffset);
            break;
        case 2:
            rv=pcontext.bb.getShort(byteOffset);
            break;
        case 3:
            if(ide.getByteOrder()==ByteOrder.BIG_ENDIAN) {
                rv = (pcontext.bb.getShort(byteOffset) & 0xFFFF) << 8;
                rv += pcontext.bb.get(byteOffset + 2) & 0xFF;
            } else {
                rv = pcontext.bb.getShort(byteOffset) & 0xFFFF;
                rv += (pcontext.bb.get(byteOffset + 2) & 0xFF) << 16;
            }
            break;
        case 4:
            rv=pcontext.bb.getInt(byteOffset);
            break;
        default:
        	if( ide.getEncoding() != IntegerDataEncoding.Encoding.string ) {
        		log.warn(String.format("parameter extraction for %d bytes not supported, used for %s", byteSize, ide.getName()));
        	}
        }
        //pcontext.bitPosition+=ide.getSizeInBits();
        switch(ide.getEncoding()) {
        case twosCompliment:
        	pcontext.bitPosition+=ide.getSizeInBits();
            //we shift it to the left first such that the sign bit arrives on the first position
            rv= (rv&mask)<<(64-ide.getSizeInBits()-bitsToShift);
            rv=rv>>(64-ide.getSizeInBits());
            pv.setRawSignedInteger((int)rv);
            if(ide.getDefaultCalibrator()==null)
                return Long.valueOf(rv);
            else return ide.getDefaultCalibrator().calibrate((double)rv);
        case unsigned:
        	pcontext.bitPosition+=ide.getSizeInBits();
            //we use the ">>>" such that the sign bit is not carried
            rv=(rv&mask)>>>bitsToShift;
            //System.out.println("extracted rv="+rv+" from byteOffset="+byteOffset+" using mask="+mask+" and bitsToShift="+bitsToShift);
            pv.setRawUnsignedInteger((int)rv);
            if(ide.getDefaultCalibrator()==null)
                return Long.valueOf(rv);
            else return ide.getDefaultCalibrator().calibrate((double)(rv&0xFFFFFFFFL));
        case string:
        	String s=(String)extractRawAndCalibrate(ide.getStringEncoding(), pv);
        	long l = Long.valueOf( s );
        	if( ide.getDefaultCalibrator()==null ) {
        		return new Long( l ); 
        	} else {
        		return ide.getDefaultCalibrator().calibrate( (double)l );
        	}
        default:
            throw new UnsupportedOperationException("encoding "+ide.getEncoding()+" not implemented");
        }
    }

    private Object extractRawAndCalibrateString(StringDataEncoding sde, ParameterValue pv) {
        if(pcontext.bitPosition%8!=0) log.warn("String Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition);
        int sizeInBytes=0;
        switch(sde.getSizeType()) {
        case Fixed:
            sizeInBytes=sde.getSizeInBits()/8;
            break;
        case LeadingSize:
            for(int i=0;i<sde.getSizeInBitsOfSizeTag();i+=8) {
                sizeInBytes=(sizeInBytes<<8) + pcontext.bb.get(pcontext.bitPosition/8);
                pcontext.bitPosition+=8;
            }
            break;
        case TerminationChar:
            int byteOffset=pcontext.bitPosition/8;
            while(pcontext.bb.get(byteOffset)!=sde.getTerminationChar()){ 
                sizeInBytes++;
                byteOffset++;
            }
            break;
        }
        byte[] b=new byte[sizeInBytes];
        System.out.println(sde.getName()+" Extracting string of size "+sizeInBytes+" para sizeInBytes of size tag="+sde.getSizeInBitsOfSizeTag()+" bitposition: "+pcontext.bitPosition);
        pcontext.bb.position(pcontext.bitPosition/8);
        pcontext.bb.get(b);
        pv.setRawValue(b);
        pv.setBitSize(8*sizeInBytes);
        pcontext.bitPosition+=8*sizeInBytes;
        if(sde.getSizeType()==SizeType.TerminationChar) {
            pcontext.bitPosition+=8;//the termination char
        }
        
        return new String(b);
    }

    private Object extractRawAndCalibrateFloat(FloatDataEncoding de, ParameterValue pv) {
        if(pcontext.bitPosition%8!=0) log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition); 
        pcontext.bb.order(de.getByteOrder());
        switch(de.getEncoding()) {
        case IEEE754_1985:
            return extractRawAndCalibrateIEEE754_1985(de, pv);
        case STRING:
            String s=(String) extractRawAndCalibrate(de.getStringDataEncoding(), pv);
            double d=Double.valueOf(s);
            if(de.getDefaultCalibrator()==null) {
                return new Double(d);
            } else {
                return de.getDefaultCalibrator().calibrate(d);
            }
        default:
            throw new RuntimeException("Float Encoding "+de.getEncoding()+" not implemented");
        }
    }

    private Object extractRawAndCalibrateIEEE754_1985(FloatDataEncoding de, ParameterValue pv) {
        if(pcontext.bitPosition%8!=0) log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition); 
        pcontext.bb.order(de.getByteOrder());
        int byteOffset=pcontext.bitPosition/8;
        pcontext.bitPosition+=de.getSizeInBits();
        if(de.getSizeInBits()==32) {
            float f=pcontext.bb.getFloat(byteOffset);
            pv.setRawValue(f);
            if(de.getDefaultCalibrator()==null) {
                return new Float(f);
            } else {
                return de.getDefaultCalibrator().calibrate(f);
            }
        } else {
            double d=pcontext.bb.getDouble(byteOffset);
            pv.setRawValue(d);
            if(de.getDefaultCalibrator()==null) {
                return new Double(d);
            } else {
                return de.getDefaultCalibrator().calibrate(d);
            }
        }
    }


    private Object extractRawAndCalibrateBinary(BinaryDataEncoding bde, ParameterValue pv) {
        if(pcontext.bitPosition%8!=0) log.warn("Binary Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition); 

        byte[] b=new byte[bde.getSizeInBits()/8];
        pcontext.bb.position(pcontext.bitPosition/8);
        pcontext.bb.get(b);
        pv.setRawValue(b);
        pcontext.bitPosition+=bde.getSizeInBits();
        return b;
    }
}
