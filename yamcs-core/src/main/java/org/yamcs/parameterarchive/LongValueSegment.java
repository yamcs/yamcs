package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.LongArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

public class LongValueSegment extends BaseSegment implements ValueSegment {

    final static int SUBFORMAT_ID_RAW = 0;
    LongArray values;

    // all possible types that can be stored by this segment
    static final Type[] types = new Type[] { Type.UINT64, Type.SINT64, Type.TIMESTAMP };
    int numericType;// index in the array above

    LongValueSegment(Type type) {
        super(FORMAT_ID_LongValueSegment);
        values = new LongArray();
        this.numericType = getNumericType(type);
    }

    private int getNumericType(Type type) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                return i;
            }
        }
        throw new IllegalStateException();
    }

    private LongValueSegment() {
        super(FORMAT_ID_IntValueSegment);
    }

    @Override
    public void writeTo(ByteBuffer bb) {
        writeHeader(SUBFORMAT_ID_RAW, bb);
        int n = values.size();
        VarIntUtil.writeVarInt32(bb, n);
        for (int i = 0; i < n; i++) {
            bb.putLong(values.get(i));
        }
    }

    // write header:
    // 1st byte: spare   type   subformatid
    //           2 bits  2 bits 4 bits    
    private void writeHeader(int subFormatId, ByteBuffer bb) {
        int x = (numericType << 4) | subFormatId;
        bb.put((byte) x);
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        byte x = bb.get();
        int subFormatId = x & 0xF;
        if (subFormatId != SUBFORMAT_ID_RAW)
            throw new DecodingException("Unknown subformatId " + subFormatId + " for LongValueSegment");
        
        numericType = (x >> 4) & 3;

        int n = VarIntUtil.readVarInt32(bb);

        if (bb.limit() - bb.position() < 8 * n) {
            throw new DecodingException("Cannot decode long segment: expected " + (8 * n) + " bytes and only "
                    + (bb.limit() - bb.position()) + " available");
        }
        values = new LongArray(n);
        for (int i = 0; i < n; i++) {
            values.add(bb.getLong());
        }
    }

    public static LongValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        LongValueSegment r = new LongValueSegment();
        r.parse(bb);
        return r;
    }

    public static LongValueSegment consolidate(List<Value> values, Type type) {
        LongValueSegment segment = new LongValueSegment(type);
        int n = values.size();

        if (type==Type.SINT64) {
            for (int i = 0; i < n; i++) {
                segment.add(values.get(i).getSint64Value());
            }
        } else if(type==Type.UINT64) {
            for (int i = 0; i < n; i++) {
                segment.add(values.get(i).getUint64Value());
            }
        } else {
            for (int i = 0; i < n; i++) {
                segment.add(values.get(i).getTimestampValue());
            }
        }
        return segment;
    }

    private void add(long x) {
        values.add(x);
    }

    @Override
    public int getMaxSerializedSize() {
        return 4 + 8 * values.size(); // 4 for the size plus 8 for each element
    }

    @Override
    public ValueArray getRange(int posStart, int posStop, boolean ascending) {
        long[] r = new long[posStop - posStart];
        if (ascending) {
            for (int i = posStart; i < posStop; i++) {
                r[i - posStart] = values.get(i);
            }
        } else {
            for (int i = posStop; i > posStart; i--) {
                r[posStop - i] = values.get(i);
            }
        }
        return new ValueArray(types[numericType], r);
    }

    @Override
    public Value getValue(int index) {
        if (numericType==0) {
            return ValueUtility.getUint64Value(values.get(index));
        } else if (numericType==1){
            return ValueUtility.getSint64Value(values.get(index));
        } else {
            return ValueUtility.getTimestampValue(values.get(index));
        }
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public void add(int pos, Value v) {
        Type type = v.getType();
        if (type == Type.UINT64) {
            values.add(pos, v.getUint64Value());
        } else if (type == Type.SINT64) {
            values.add(pos, v.getSint64Value());
        } else {
            values.add(pos, v.getTimestampValue());
        }
    }

    @Override
    public LongValueSegment consolidate() {
        return this;
    }

}
