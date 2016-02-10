package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.VarIntUtil;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * GenericValueSegment keeps an ArrayList of Values. 
 * It is used during the archive buildup and as a catch all for non optimized ValueSegments.
 * 
 * 
 * 
 */
public class GenericValueSegment extends BaseSegment implements ValueSegment {
    List<Value> values = new ArrayList<Value>();

    public GenericValueSegment() {
        super(FORMAT_ID_GenericValueSegment);
    }


    public void add(int pos, Value v) {
        values.add(pos, v);
    }


    /**
     * Encode using regular protobuf delimited field writes
     */
    @Override
    public void writeTo(ByteBuffer bb) {
        VarIntUtil.writeVarInt32(bb, values.size());
        for(Value v: values) {
            byte[] b = v.toByteArray();
            VarIntUtil.writeVarInt32(bb, b.length);
            bb.put(b);
        }
    }
    
    
    /**
     * Decode using regular protobuf delimited field writes
     */
    private void parse(ByteBuffer bb) throws DecodingException {
        int num = VarIntUtil.readVarInt32(bb);
        for(int i=0;i<num; i++) {
            int size = VarIntUtil.readVarInt32(bb);
            byte[] b = new byte[size];
            bb.get(b);
            try {
                values.add(Value.parseFrom(b));
            } catch (InvalidProtocolBufferException e) {
                throw new DecodingException("Failed to decode Value: ",e);
            }
        }
    }
    
    static GenericValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        GenericValueSegment r= new GenericValueSegment();
        r.parse(bb);
        return r;
    }
    
    /**
     * Transform this generic segment in one of the specialised versions
     * @return
     */
    public BaseSegment consolidate() {
        if(values.size()==0) return this;

        Type type = values.get(0).getType();
        switch(type) {
        case UINT32:
        case SINT32:
        case BINARY:
        case STRING:
        case FLOAT:
            throw new IllegalStateException("should not be here; specific segments shall be used for this type: "+type);
        case BOOLEAN:
            return BooleanValueSegment.consolidate(values);
        case DOUBLE:
            return DoubleValueSegment.consolidate(values);
        case UINT64:
            return LongValueSegment.consolidate(values, false);
        case SINT64:
            return LongValueSegment.consolidate(values, true);
        case TIMESTAMP:

        default:
            return this;
        }
    }

    @Override
    public int getMaxSerializedSize() {
        int size = 4*values.size(); //max 4 bytes for storing each value's size
        for(Value v: values) {
            size += v.getSerializedSize();
        }
        return size;
    }

    @Override
    public Value getValue(int index) {
        return values.get(index);
    }

    @Override
    public Value[] getRange(int posStart, int posStop, boolean ascending) {
        Value[] r = new Value[posStop-posStart];
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
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        GenericValueSegment other = (GenericValueSegment) obj;
        if (values == null) {
            if (other.values != null)  return false;
            else return true;
        } else {
            if(other.values==null) return false;
        }

        return equal(values, other.values);
    }

    private static boolean equal(List<Value> values1, List<Value> values2) {
        if(values1.size()!=values2.size()) return false;
        for(int i=0; i<values1.size(); i++) {
            Value v1 = values1.get(i);
            Value v2 = values2.get(i);
            byte[] b1 = v1.toByteArray();
            byte[] b2 = v2.toByteArray();
            if(!Arrays.equals(b1, b2)) return false;
        }
        return true;
    }


    public int size() {
        return values.size();
    }
}
