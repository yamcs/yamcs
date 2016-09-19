package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.DoubleArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;


public class DoubleValueSegment extends BaseSegment implements ValueSegment {
    final static byte SUBFORMAT_ID_RAW = 0;
    
    DoubleArray values;
    
    DoubleValueSegment() {
        super(FORMAT_ID_DoubleValueSegment);
        values = new DoubleArray();
    }
   
            
    @Override
    public void writeTo(ByteBuffer bb) {
        bb.put(SUBFORMAT_ID_RAW);
        int n = values.size();
        VarIntUtil.writeVarInt32(bb, n);
        for(int i=0;i<n;i++) {
            bb.putDouble(values.get(i));
        }
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        byte fid = bb.get();
        if(fid!=SUBFORMAT_ID_RAW) {
            throw new DecodingException("Uknown sub format id: "+fid);
        }
        int n = VarIntUtil.readVarInt32(bb);
        values = new DoubleArray(n);
        
        for(int i=0;i<n;i++) {
            values.add(bb.getDouble());
        }
    }
    public static DoubleValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        DoubleValueSegment r = new DoubleValueSegment();
        r.parse(bb);
        return r;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getDoubleValue(values.get(index));
    }

    @Override
    public int getMaxSerializedSize() {
        return 4+8*values.size();
    }
    
    static DoubleValueSegment consolidate(List<Value> v) {
        DoubleValueSegment fvs = new DoubleValueSegment();
        int n = v.size();
        fvs.values = new DoubleArray(n);
        for(int i=0; i<n; i++) {
            fvs.values.add(v.get(i).getDoubleValue());
        }
        return fvs;
    }
    
    @Override
    public double[] getRange(int posStart, int posStop, boolean ascending) {
        double[] r = new double[posStop-posStart];
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
    public int size() {
        return values.size();
    }
    
    @Override
    public void add(int pos, Value engValue) {
        values.add(pos, engValue.getDoubleValue());
    }

    @Override
    public DoubleValueSegment consolidate() {
        return this;
    }
}
