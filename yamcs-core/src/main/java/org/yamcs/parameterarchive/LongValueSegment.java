package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

public class LongValueSegment extends BaseSegment implements ValueSegment {
    boolean signed;
    final static int SUBFORMAT_ID_RAW = 0;
    
    LongValueSegment(boolean signed) {
        super(FORMAT_ID_UInt64ValueSegment);
        this.signed = signed;
    }


    long[] values;
    
    @Override
    public void writeTo(ByteBuffer bb) {
        writeHeader(SUBFORMAT_ID_RAW,bb);
        int n = values.length;
        VarIntUtil.writeVarInt32(bb, n);
        for(int i=0; i<n; i++) {
            VarIntUtil.writeVarInt64(bb, values[i]);
        }
    }

    //write header:
    // 1st byte:    spare    signed/unsigned subformatid
    //              3 bits   1 bit           4 bits
    private void writeHeader(int subFormatId, ByteBuffer bb) {
        int x = signed?1:0;
        x=(x<<4)|subFormatId;
        bb.put((byte)x);
    }

    
    private void parse(ByteBuffer bb) throws DecodingException {
        byte x = bb.get();
        int subFormatId = x&0xF;
        if(subFormatId!=SUBFORMAT_ID_RAW) throw new DecodingException("Unknown subformatId "+subFormatId+" for LongValueSegment");
        signed = (((x>>4)&1)==1);
        
        int n = VarIntUtil.readVarInt32(bb);
        values = new long[n];
        for(int i=0; i<n; i++) {
            values[i]=VarIntUtil.readVarInt64(bb);
        }
    }

    public static LongValueSegment parseFrom(ByteBuffer bb, boolean signed) throws DecodingException {
        LongValueSegment r = new LongValueSegment(signed);
        r.parse(bb);
        return r;
    }
    
    public static LongValueSegment  consolidate(List<Value> values, boolean signed) {
        LongValueSegment segment = new LongValueSegment(signed);
        int n = values.size();
        segment.values = new long[n];
        for(int i =0;i<n; i++) {
            segment.values[i] = values.get(i).getUint64Value();
        }
        return segment;
    }

    @Override
    public int getMaxSerializedSize() {
        return 4+8*values.length; //4 for the size plus 8 for each element
    }

    @Override
    public long[] getRange(int posStart, int posStop, boolean ascending) {
        long[] r = new long[posStop-posStart];
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = values[i];
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = values[i];
            }
        }
        
        return r;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getUint64Value(values[index]);
    }

    @Override
    public int size() {
        return values.length;
    }
    
    @Override
    public void add(int pos, Value engValue) {
        throw new UnsupportedOperationException("add not supported");
        
    }

    @Override
    public BaseSegment consolidate() {
        throw new UnsupportedOperationException("consolidate not supported");
    }
    
}
