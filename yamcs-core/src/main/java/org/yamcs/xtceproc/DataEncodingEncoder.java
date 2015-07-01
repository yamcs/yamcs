package org.yamcs.xtceproc;

import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding;

/**
 * Encodes TC data according to the DataEncoding definition 
 * @author nm
 *
 */
public class DataEncodingEncoder {
    TcProcessingContext pcontext;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    public DataEncodingEncoder(TcProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    /**
     *  Encode the raw value of th eargument into the packet.
     */
   public void encodeRaw(DataEncoding de, Value rawValue) {
        if(de instanceof IntegerDataEncoding) {
            encodeRawInteger((IntegerDataEncoding) de, rawValue);
        } else if(de instanceof FloatDataEncoding) {
            encodeRawFloat((FloatDataEncoding) de, rawValue);
        } else if(de instanceof StringDataEncoding) {
            encodeRawString((StringDataEncoding) de, rawValue);
        } else if(de instanceof BinaryDataEncoding) {
            encodeRawBinary((BinaryDataEncoding) de, rawValue);
        } else {
            log.error("DataEncoding "+de+" not implemented");
            throw new RuntimeException("DataEncoding "+de+" not implemented");
        }
    }

    private void encodeRawInteger(IntegerDataEncoding ide, Value rawValue) {
        // Integer encoded as string, don't even try reading it as int
        if(ide.getEncoding() == Encoding.string) {
            encodeRaw(ide.getStringEncoding(), rawValue);
            return;
        }
        
        //STEP 0 convert the value to a long
        long v;
        switch (rawValue.getType()) {
        case SINT32:
        	v = rawValue.getSint32Value();
        	break;
        case SINT64:
        	v = rawValue.getSint64Value();
        	break;
        case UINT32:
        	v = rawValue.getUint32Value() &0xFFFFFFFFL;
        	break;
        case UINT64:
        	v = rawValue.getUint64Value();
        	break;
        case BOOLEAN:
        	v = rawValue.getBooleanValue()?1:0;
        	break;
        case DOUBLE:
        	v = (long) rawValue.getDoubleValue();
        	break;
        case FLOAT:
        	v = (long) rawValue.getFloatValue();
        	break;
        default:
        	throw new IllegalArgumentException("Cannot encode values of types " + rawValue.getType() + " to string");        	
        }
	
        
        //STEP 1 extract 8 bytes from the buffer into the long x.
        // The first extracted byte is where the first bit of v should fit in
        //NOTE: in order to do this for the last arguments, the byte buffer has to be longer than the packet
        int byteOffset = (pcontext.bitPosition)/8;
        int bitOffsetInsideMask = pcontext.bitPosition-8*byteOffset;
        int bitsToShift = 64 - bitOffsetInsideMask - ide.getSizeInBits();
        long mask = ~((-1L<<(64-ide.getSizeInBits()))>>>(64-ide.getSizeInBits()-bitsToShift));
        pcontext.bb.order(ide.getByteOrder());
        
        long x = pcontext.bb.getLong(byteOffset);
        pcontext.bitPosition+=ide.getSizeInBits();
        
        //STEP 2 mix the extracted bytes x with he value of the argument v, depending on the encoding type        
        x = x & mask;
        switch(ide.getEncoding()) {
        case twosCompliment:
        	x |= (v<<(64-ide.getSizeInBits())>>>(64-ide.getSizeInBits()-bitsToShift));
        	break;
        case unsigned:
        	x |= (v<<bitsToShift);
            break;
        case signMagnitude:
        	if(v<0) {
        		v = -v;
        		v &= (1<<bitsToShift);
        	};
        	x |= (v<<bitsToShift);        	
            break;
        default:
            throw new UnsupportedOperationException("encoding "+ide.getEncoding()+" not implemented");
        }
        //STEP 3 put back the extracted bytes into the buffer
        pcontext.bb.putLong(byteOffset, x);
    }
    

    private void encodeRawString(StringDataEncoding sde, Value rawValue) {
    	if(rawValue.getType()!=Type.STRING) {
    		throw new IllegalStateException("String encoding requires data of type String not "+rawValue.getType());
    	}
    	String v = rawValue.getStringValue();
    	if(pcontext.bitPosition%8!=0) {
        	throw new IllegalStateException("String Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition);        	
        }
        
    	byte[] b = v.getBytes(); //TBD
    	
        switch(sde.getSizeType()) {
        case Fixed:
        	int byteOffset = pcontext.bitPosition/8;
            int sizeInBytes=sde.getSizeInBits()/8;
            if(sizeInBytes>b.length) sizeInBytes = b.length;
            pcontext.bb.position(byteOffset);
            pcontext.bb.put(b, 0, sizeInBytes);
            byte[] blank =new byte[sde.getSizeInBits() - sizeInBytes];
            pcontext.bb.put(blank);
            pcontext.bitPosition+=sde.getSizeInBits();
            break;
        case LeadingSize:
        	pcontext.bb.order(ByteOrder.BIG_ENDIAN); //TBD
        	byteOffset = pcontext.bitPosition/8;
        	switch(sde.getSizeInBitsOfSizeTag()) {
        		case 8:
        			pcontext.bb.put(byteOffset, (byte) b.length);
        			pcontext.bitPosition+=8;
        			break;
        		case 16: 
        			pcontext.bb.putShort(byteOffset, (short) b.length);
        			pcontext.bitPosition+=16;
        			break;
        		case 32: 
        			pcontext.bb.putInt(byteOffset, (short) b.length);
        			pcontext.bitPosition+=32;
        			break;
        		default:
        			throw new IllegalArgumentException("SizeInBits of Size Tag of "+sde.getSizeInBitsOfSizeTag()+" not supported");        		
        	}
        	byteOffset = pcontext.bitPosition/8;
            pcontext.bb.position(byteOffset);
        	pcontext.bb.put(b, 0, b.length);
            pcontext.bitPosition = pcontext.bb.position() * 8;
            break;
        case TerminationChar:
            byteOffset=pcontext.bitPosition/8;
            pcontext.bb.position(byteOffset);
            pcontext.bb.put(b, 0, b.length);
            pcontext.bb.put(sde.getTerminationChar());
            pcontext.bitPosition = pcontext.bb.position() * 8;
            break;
        }
    }

    private void encodeRawFloat(FloatDataEncoding de, Value rawValue) {
        if(pcontext.bitPosition%8!=0) log.warn("Float Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition); 
        pcontext.bb.order(de.getByteOrder());
        switch(de.getEncoding()) {
        case IEEE754_1985:
            encodeRawIEEE754_1985(de, rawValue);
            break;
        case STRING:
            encodeRaw(de.getStringDataEncoding(), rawValue);
            break;
        default:
            throw new RuntimeException("Float Encoding "+de.getEncoding()+" not implemented");
        }
    }

    private void encodeRawIEEE754_1985(FloatDataEncoding de, Value rawValue) {
        if(pcontext.bitPosition%8!=0) {
        	throw new IllegalArgumentException("Float Argument that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition);        	
        }
        double v;
        switch(rawValue.getType()) {
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
        	throw new IllegalArgumentException("Float encoding for data of type "+rawValue.getType()+" not supported");
        }
        
        pcontext.bb.order(de.getByteOrder());
        int byteOffset=pcontext.bitPosition/8;
        if(de.getSizeInBits()==32) {
            pcontext.bb.putFloat(byteOffset, (float)v);            
        } else {
        	pcontext.bb.putDouble(byteOffset, v);
        }
        pcontext.bitPosition+=de.getSizeInBits();
    }

    private void encodeRawBinary(BinaryDataEncoding bde, Value rawValue) {
        if(pcontext.bitPosition%8!=0) {
        	throw new IllegalStateException("Binary Parameter that does not start at byte boundary not supported. bitPosition:"+pcontext.bitPosition);        
        }
        byte[] v;
        if(rawValue.getType()==Type.BINARY) {
        	v = rawValue.getBinaryValue().toByteArray(); 
        } else if (rawValue.getType() == Type.STRING) {
        	v = rawValue.getStringValue().getBytes(); //TBD encoding
        } else {
        	throw new IllegalArgumentException("Cannot encode as binary data values of type "+rawValue.getType());
        }
        int sizeInBytes = bde.getSizeInBits()/8;
        if(sizeInBytes>v.length) sizeInBytes = v.length;
        pcontext.bb.put(v, 0, sizeInBytes);
        pcontext.bitPosition+=bde.getSizeInBits();
    }
    

}
