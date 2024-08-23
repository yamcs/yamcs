package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.BooleanArray;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

/**
 * Boolean value sgement uses a boolean array backed by a long[] to represent the boolean values as a set of bits
 * 
 */
public class BooleanValueSegment extends BaseSegment implements ValueSegment {
    BooleanArray ba;

    public BooleanValueSegment() {
        super(FORMAT_ID_BooleanValueSegment);
        ba = new BooleanArray();
    }

    @Override
    public void insert(int pos, Value value) {
        ba.add(pos, value.getBooleanValue());
    }

    @Override
    public void add(Value value) {
        ba.add(value.getBooleanValue());
    }

    /**
     * write the segment to buffer
     * 
     * @param bb
     */
    @Override
    public void writeTo(ByteBuffer bb) {
        VarIntUtil.writeVarInt32(bb, ba.size());

        long[] la = ba.toLongArray();
        VarIntUtil.writeVarInt32(bb, la.length);

        for (long l : la) {
            bb.putLong(l);
        }
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        int size = VarIntUtil.readVarInt32(bb);
        int n = VarIntUtil.readVarInt32(bb);
        long[] la = new long[n];
        for (int i = 0; i < n; i++) {
            la[i] = bb.getLong();
        }
        ba = BooleanArray.valueOf(la, size);
    }

    public static BooleanValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        BooleanValueSegment r = new BooleanValueSegment();
        r.parse(bb);
        return r;
    }

    @Override
    public int getMaxSerializedSize() {
        // 4 bytes max for the segment size
        // 4 bytes max for the long array length
        // 8 bytes for each 64 bits, rounded up
        return 16 + ba.size() / 8;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getBooleanValue(ba.get(index));
    }

    static BooleanValueSegment consolidate(List<Value> values) {
        BooleanValueSegment bvs = new BooleanValueSegment();
        int n = values.size();

        bvs.ba = new BooleanArray(n);
        for (int i = 0; i < n; i++) {
            bvs.ba.add(i, values.get(i).getBooleanValue());
        }
        return bvs;
    }

    @Override
    public ValueArray getRange(int posStart, int posStop, boolean ascending) {
        ValueArray r = new ValueArray(Type.BOOLEAN, posStop - posStart);
        if (ascending) {
            for (int i = posStart; i < posStop; i++) {
                r.setValue(i - posStart, ba.get(i));
            }
        } else {
            for (int i = posStop; i > posStart; i--) {
                r.setValue(posStop - i, ba.get(i));
            }
        }

        return r;
    }

    /**
     * returns the size of the BitSet storing the values - this will round up to the size of long
     */
    @Override
    public int size() {
        return ba.size();
    }
}
