package org.yamcs.parameterarchive;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.FloatArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;


public class FloatValueSegment extends BaseSegment implements ValueSegment {
    final static byte SUBFORMAT_ID_RAW = 0;
    final static byte SUBFORMAT_ID_COMPRESSED = 1;
    

   FloatArray values;
   
   FloatValueSegment() {
       super(FORMAT_ID_FloatValueSegment);
       values = new FloatArray();
   }

    @Override
    public void writeTo(ByteBuffer bb) {
        int position = bb.position();

        //try to write it compressed, if we get an buffer overflow, revert to raw encoding
        bb.put(SUBFORMAT_ID_COMPRESSED);
        int n = values.size();
        VarIntUtil.writeVarInt32(bb, n);

        try {
            FloatCompressor.compress(values.array(), values.size(), bb);
        } catch (BufferOverflowException e) {
            bb.position(position);
            writeRaw(bb);
        }
    }

    private void writeRaw(ByteBuffer bb) {
        bb.put(SUBFORMAT_ID_RAW);
        int n = values.size();
        VarIntUtil.writeVarInt32(bb, n);
        for(int i=0; i<n; i++) {
            bb.putFloat(values.get(i));
        }

    }

    private void parse(ByteBuffer bb) throws DecodingException {
        byte b= bb.get();
        int n = VarIntUtil.readVarInt32(bb);
        float[] floats;
        if(b==SUBFORMAT_ID_RAW) {
            floats = new float[n];
            for(int i=0;i<n;i++) {
                floats[i] = bb.getFloat();
            }	 
        } else if(b==SUBFORMAT_ID_COMPRESSED) {
            floats = FloatCompressor.decompress(bb, n);	
        } else {
            throw new DecodingException("Unknown SUBFORMAT_ID: "+b);
        }
        values = FloatArray.wrap(floats);
    }

    public static FloatValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        FloatValueSegment r = new FloatValueSegment();
        r.parse(bb);
        return r;
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getFloatValue(values.get(index));
    }

    @Override
    public int getMaxSerializedSize() {
        return 5+4*values.size()+1;
    }

    @Override
    public float[] getRange(int posStart, int posStop, boolean ascending) {
        float[] r = new float[posStop-posStart];
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
    
    static FloatValueSegment consolidate(List<Value> values) {
        FloatValueSegment fvs = new FloatValueSegment();
        int n = values.size();
        fvs.values = new FloatArray(n);
        for(int i=0; i<n; i++) {
            fvs.values.add(values.get(i).getFloatValue());
        }
        return fvs;
    }


    @Override
    public int size() {
        return values.size();
    }
    
    @Override
    public void add(int pos, Value engValue) {
        values.add(pos, engValue.getFloatValue());
        
    }

    @Override
    public BaseSegment consolidate() {
        return this;
    }
}
