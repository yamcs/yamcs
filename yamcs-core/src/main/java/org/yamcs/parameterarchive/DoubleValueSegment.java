package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;


public class DoubleValueSegment extends BaseSegment implements ValueSegment {
    final static byte SUBFORMAT_ID_RAW = 0;
    
    double[] doubles;
    
    DoubleValueSegment() {
        super(FORMAT_ID_DoubleValueSegment);
    }
   
            
    @Override
    public void writeTo(ByteBuffer bb) {
        bb.put(SUBFORMAT_ID_RAW);
        int n = doubles.length;
        VarIntUtil.writeVarInt32(bb, n);
        for(int i=0;i<n;i++) {
            bb.putDouble(doubles[i]);
        }
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        byte fid = bb.get();
        if(fid!=SUBFORMAT_ID_RAW) {
            throw new DecodingException("Uknown sub format id: "+fid);
        }
        int n = VarIntUtil.readVarInt32(bb);
        doubles = new double[n];
        
        for(int i=0;i<n;i++) {
            doubles[i]= bb.getDouble();
        }
    }
    public static DoubleValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        DoubleValueSegment r = new DoubleValueSegment();
        r.parse(bb);
        return r;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getDoubleValue(doubles[index]);
    }

    @Override
    public int getMaxSerializedSize() {
        return 4+8*doubles.length;
    }
    
    static DoubleValueSegment consolidate(List<Value> values) {
        DoubleValueSegment fvs = new DoubleValueSegment();
        int n = values.size();
        fvs.doubles = new double[n];
        for(int i=0; i<n; i++) {
            fvs.doubles[i] = values.get(i).getDoubleValue();
        }
        return fvs;
    }
    
    @Override
    public double[] getRange(int posStart, int posStop, boolean ascending) {
        double[] r = new double[posStop-posStart];
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = doubles[i];
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = doubles[i];
            }
        }
        
        return r;
    }


    @Override
    public int size() {
        return doubles.length;
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
