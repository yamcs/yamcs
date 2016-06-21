package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.DecodingException;

/**
 * Base class for all segments of values, timestamps or ParameterStatus
 * 
 * @author nm
 *
 */
public abstract class BaseSegment {
    public static final byte FORMAT_ID_SortedTimeValueSegment = 1;
    public static final byte FORMAT_ID_ParameterStatusSegment = 2;
    public static final byte FORMAT_ID_GenericValueSegment = 10;    
    public static final byte FORMAT_ID_IntValueSegment = 11;
    public static final byte FORMAT_ID_StringValueSegment = 13;
    @Deprecated
    public static final byte FORMAT_ID_OldBooleanValueSegment = 15;
    public static final byte FORMAT_ID_FloatValueSegment = 16;
    public static final byte FORMAT_ID_DoubleValueSegment = 17;
    public static final byte FORMAT_ID_LongValueSegment = 18;
    public static final byte FORMAT_ID_BinaryValueSegment = 19;
    public static final byte FORMAT_ID_BooleanValueSegment = 20;
    
    protected byte formatId;
    
    BaseSegment(byte formatId) {
        this.formatId = formatId;
    }
    
  

    public abstract void writeTo(ByteBuffer buf);
    
    /**
     * 
     * @return a high approximation for the serialized size in order to allocate a ByteBuffer big enough
     */
    public abstract int getMaxSerializedSize();
   
    
    /**
     * returns an array containing the values in the range [posStart, posStop) if ascending or [posStop, posStart) if descending
     * 
     * @param posStart
     * @param posStop
     * @param ascending
     * @return an array containing the values in the specified range
     */
    public abstract Object getRange(int posStart, int posStop, boolean ascending) ;
    
    public byte getFormatId() {
        return formatId;
    }

    public static BaseSegment parseSegment(byte formatId, long segmentStart, ByteBuffer bb) throws DecodingException {
        switch(formatId) {
        case FORMAT_ID_ParameterStatusSegment:
            return ParameterStatusSegment.parseFrom(bb);
        case FORMAT_ID_SortedTimeValueSegment:
            return SortedTimeSegment.parseFrom(bb, segmentStart);
        case FORMAT_ID_GenericValueSegment:
            return GenericValueSegment.parseFrom(bb);    
        case FORMAT_ID_IntValueSegment:
            return IntValueSegment.parseFrom(bb);
        case FORMAT_ID_StringValueSegment:
            return  StringValueSegment.parseFrom(bb);
        case FORMAT_ID_BooleanValueSegment:
            return OldBooleanValueSegment.parseFrom(bb);
        case FORMAT_ID_FloatValueSegment:
            return FloatValueSegment.parseFrom(bb);
        case FORMAT_ID_DoubleValueSegment:
            return DoubleValueSegment.parseFrom(bb);
        case FORMAT_ID_LongValueSegment:
            return LongValueSegment.parseFrom(bb);
        case FORMAT_ID_BinaryValueSegment:
            return BinaryValueSegment.parseFrom(bb);
        default:
          throw new DecodingException("Invalid format id "+formatId); 
        }
    }
    
    public abstract int size();
}
