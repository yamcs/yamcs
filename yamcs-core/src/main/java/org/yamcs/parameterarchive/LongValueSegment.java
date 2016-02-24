package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.LongArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

public class LongValueSegment extends BaseSegment implements ValueSegment {
    boolean signed;
    final static int SUBFORMAT_ID_RAW = 0;
    LongArray values;


    LongValueSegment(boolean signed) {
        super(FORMAT_ID_LongValueSegment);
        values = new LongArray();
        this.signed = signed;
    }


    private LongValueSegment() {
        super(FORMAT_ID_IntValueSegment);
    }
    
    @Override
    public void writeTo(ByteBuffer bb) {
        writeHeader(SUBFORMAT_ID_RAW, bb);
        int n = values.size();
        VarIntUtil.writeVarInt32(bb, n);
        for(int i=0; i<n; i++) {
            bb.putLong(values.get(i));
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
        
        if(bb.limit()-bb.position() < 8*n) {
            throw new DecodingException("Cannot decode long segment: expected "+(8*n)+" bytes and only "+(bb.limit()-bb.position())+ " available");
        }
        values = new LongArray(n);
        for(int i=0; i<n; i++) {
            values.add(bb.getLong());
        }
    }

    public static LongValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        LongValueSegment r = new LongValueSegment();
        r.parse(bb);
        return r;
    }

    public static LongValueSegment  consolidate(List<Value> values, boolean signed) {
        LongValueSegment segment = new LongValueSegment(signed);
        segment.signed = signed;
        int n = values.size();

        if(signed) {
            for(int i =0;i<n; i++) {
                segment.add(values.get(i).getSint64Value());
            } 
        } else {
            for(int i =0;i<n; i++) {
                segment.add(values.get(i).getUint64Value());
            }
        }
        return segment;
    }

    private void add(long x) {
        values.add(x);
    }


    @Override
    public int getMaxSerializedSize() {
        return 4+8*values.size(); //4 for the size plus 8 for each element
    }

    @Override
    public long[] getRange(int posStart, int posStop, boolean ascending) {
        long[] r = new long[posStop-posStart];
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = values.get(i);
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = values.get(i);
            }
        }

        return r;
    }

    @Override
    public Value getValue(int index) {
        if(signed) {
            return ValueUtility.getSint64Value(values.get(index));
        } else {
            return ValueUtility.getUint64Value(values.get(index));
        }
    }

    @Override
    public int size() {
        return values.size();
    }


    @Override
    public void add(int pos, Value v) {
        if(signed) {
            values.add(pos, v.getSint64Value());
        } else {
            values.add(pos, v.getUint64Value());
        }
    }


    @Override
    public LongValueSegment consolidate() {
        return this;
    }

}
