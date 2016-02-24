package org.yamcs.parameterarchive;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

/**
 * 32 bit integers  
 * encoded as deltas of deltas (good if the values are relatively constant or in increasing order)
 *  
 * @author nm
 *
 */
public class IntValueSegment extends BaseSegment implements ValueSegment {
    final static int SUBFORMAT_ID_RAW = 0; //uncompresed
    final static int SUBFORMAT_ID_DELTAZG_FPF128_VB = 1; //compressed with DeltaZigzag and then FastPFOR128 plus VariableByte for remaining
    final static int SUBFORMAT_ID_DELTAZG_VB = 2; //compressed with DeltaZigzag plus VariableByte

    private boolean signed;
    IntArray values;

    IntValueSegment(boolean signed) {
        super(FORMAT_ID_IntValueSegment);
        values = new IntArray();
        this.signed = signed;
    }


    private IntValueSegment() {
        super(FORMAT_ID_IntValueSegment);
    }



    @Override
    public void writeTo(ByteBuffer bb) {
        int position = bb.position();
        //try first to write compressed, if we fail (for random data we may exceed the buffer) then write in raw format
        try {
            writeCompressed(bb);
        } catch (IndexOutOfBoundsException|BufferOverflowException e) {
            bb.position(position);
            writeRaw(bb);
        }
    }

    public void writeCompressed(ByteBuffer bb) {
        int[] ddz = VarIntUtil.encodeDeltaDeltaZigZag(values);

        FastPFOR128 fastpfor = FastPFORFactory.get();
        int size = ddz.length;
        
        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int[] xc = new int[size];

        fastpfor.compress(ddz, inputoffset, size, xc, outputoffset);
        if (outputoffset.get() == 0) { 
            //fastpfor didn't compress anything, probably there were too few datapoints
            writeHeader(SUBFORMAT_ID_DELTAZG_VB, bb);
        } else {
            writeHeader(SUBFORMAT_ID_DELTAZG_FPF128_VB, bb);
            int length = outputoffset.get();
            for(int i=0; i<length; i++) {
                bb.putInt(xc[i]);
            }
        }
        
        //write the remaining bytes varint compressed
        for(int i = inputoffset.get(); i<size; i++) {
            VarIntUtil.writeVarInt32(bb, ddz[i]);
        }
    }


    private void writeRaw(ByteBuffer bb) {
        writeHeader(SUBFORMAT_ID_RAW, bb);
        int n = values.size();
        for(int i=0; i<n; i++) {
            bb.putInt(values.get(i));
        }
    }
    //write header:
    // 1st byte:    spare    signed/unsigned subformatid
    //              3 bits   1 bit           4 bits
    // 2nd+ bytes:  varint of n
    private void writeHeader(int subFormatId, ByteBuffer bb) {
        int x = signed?1:0;
        x=(x<<4)|subFormatId;
        bb.put((byte)x);
        VarIntUtil.writeVarInt32(bb, values.size());
    }

    static public IntValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        IntValueSegment r = new IntValueSegment();
        r.parse(bb);
        return r;
    }

    private void parse(ByteBuffer bb) throws DecodingException {
        byte x = bb.get();
        int subFormatId = x&0xF;
        signed = (((x>>4)&1)==1);
        int n = VarIntUtil.readVarInt32(bb);

        switch(subFormatId) {
        case SUBFORMAT_ID_RAW:
            parseRaw(bb, n);
            break;
        case SUBFORMAT_ID_DELTAZG_FPF128_VB: //intentional fall through
        case SUBFORMAT_ID_DELTAZG_VB: 
            parseCompressed(bb, n, subFormatId);
            break;
        default:
            throw new DecodingException("Unknown subformatId: "+subFormatId);
        }   
    }

    private void parseRaw(ByteBuffer bb, int n) {
        values = new IntArray(n);
        for(int i =0;i<n; i++) {
            values.add(bb.getInt());
        }
    }

    private void parseCompressed(ByteBuffer bb, int n, int subFormatId) throws DecodingException {
        int[] ddz = new int[n];
        
        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int position = bb.position();
        
        if(subFormatId==SUBFORMAT_ID_DELTAZG_FPF128_VB) {
            int[] x = new int[(bb.limit()-bb.position())/4];
            for(int i=0; i<x.length;i++) {
                x[i] = bb.getInt();
            }
            FastPFOR128 fastpfor = FastPFORFactory.get();
            fastpfor.uncompress(x, inputoffset, x.length, ddz, outputoffset);
            bb.position(position+inputoffset.get()*4);
        }
        
        for(int i = outputoffset.get(); i<n; i++) {
            ddz[i] = VarIntUtil.readVarInt32(bb);
        }
        values = IntArray.wrap(VarIntUtil.decodeDeltaDeltaZigZag(ddz));
    }


    public static IntValueSegment  consolidate(List<Value> values, boolean signed) {
        IntValueSegment segment = new IntValueSegment(signed);
        int n = values.size();

        segment.signed = signed;
        if(signed) {
            for(int i =0;i<n; i++) {
                segment.add(values.get(i).getSint32Value());
            }    
        } else {
            for(int i =0;i<n; i++) {
                segment.add(values.get(i).getUint32Value());
            }    
        }
        return segment;
    }


    private void add(int v) {
        values.add(v);
    }




    @Override
    public int getMaxSerializedSize() {
        return 5 + 4*values.size(); //1+for format id + 4 for the size plus 4 for each element
    }


    @Override
    public Value getValue(int index) {
        if(signed) {
            return ValueUtility.getSint32Value(values.get(index));
        } else {
            return ValueUtility.getUint32Value(values.get(index));
        }
    }

    @Override
    public int[] getRange(int posStart, int posStop, boolean ascending) {
        int[] r = new int[posStop-posStart];
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
    public void add(int pos, Value v) {
        if(signed) {
            values.add(pos, v.getSint32Value());
        } else {
            values.add(pos, v.getUint32Value());
        }
    }

    @Override
    public IntValueSegment consolidate() {
        return this;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IntValueSegment other = (IntValueSegment) obj;
        if (signed != other.signed)
            return false;
        if (values == null) {
            if (other.values != null)
                return false;
        } else if (!values.equals(other.values))
            return false;
        return true;
    }
}
