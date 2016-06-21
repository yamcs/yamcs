package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

/**
 * Old implementation of the boolean segment, not used because it doesn't remember the segment size
 * 
 * @author nm
 *
 */
@Deprecated
public class OldBooleanValueSegment extends BaseSegment implements ValueSegment {
    BitSet bitSet;
    
    
    OldBooleanValueSegment() {
        super(FORMAT_ID_OldBooleanValueSegment);
    }
    
    
    /**
     * write the segment to buffer
     * @param bb
     */
    @Override
    public void writeTo(ByteBuffer bb) {
        long[]la = bitSet.toLongArray();
        VarIntUtil.writeVarInt32(bb, la.length);
        
        for(long l:la) {
            bb.putLong(l);
        }
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        int n = VarIntUtil.readVarInt32(bb);
        long[]la = new long[n];
        for(int i=0; i<n; i++) {
            la[i]=bb.getLong();
        }
        bitSet = BitSet.valueOf(la);
    }
    
    public static OldBooleanValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        OldBooleanValueSegment r = new OldBooleanValueSegment();
        r.parse(bb);
        return r;
    }

    @Override
    public int getMaxSerializedSize() {
        return 4+bitSet.size()/8;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getBooleanValue(bitSet.get(index));
    }
    
    static OldBooleanValueSegment consolidate(List<Value> values) {
        OldBooleanValueSegment bvs = new OldBooleanValueSegment();
        int n = values.size();
        
        bvs.bitSet = new BitSet(n);
        for(int i=0; i<n; i++) {
            bvs.bitSet.set(i, values.get(i).getBooleanValue());
        }
        return bvs;
    }

    @Override
    public boolean[] getRange(int posStart, int posStop, boolean ascending) {
        boolean[] r = new boolean[posStop-posStart];
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = bitSet.get(i);
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = bitSet.get(i);
            }
        }
        
        return r;
    }


    /**
     * returns the size of the BitSet storing the values - this will round up to the size of long
     */
    @Override
    public int size() {
        return bitSet.size();
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
