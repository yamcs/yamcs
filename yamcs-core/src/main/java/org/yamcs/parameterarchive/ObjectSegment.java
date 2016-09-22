package org.yamcs.parameterarchive;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.VarIntUtil;

/**
 * Segment for all non primitive types.
 *
 * Each element is encoded to a binary that is not compressed. The compression of the segment (if any) is realized by not repeating elements.
 *  
 * Finds best encoding among:
 *  - raw - list of values stored verbatim, each preceded by its size varint32 encoded
 *  - enum - the list of unique values are stored at the beginning of the segment - each value has an implicit id (the order in the list) 
 *    - the rest of the segment is the list of ids and can be encoded in one of the following formats
 *      - VB:  varint32 of each id
 *      - FPROF: coded  with the FPROF codec + varint32 of remaining
 *      - RLE: run length encoded
 *      
 * 
 * @author nm
 *
 */
public class ObjectSegment<E> extends BaseSegment {
    final static byte SUBFORMAT_ID_RAW = 0;
    final static byte SUBFORMAT_ID_ENUM_RLE = 1;  
    final static byte SUBFORMAT_ID_ENUM_VB = 2;
    final static byte SUBFORMAT_ID_ENUM_FPROF = 3;

    //this is set only during deserialisation.
    boolean runLengthEncoded = false;

    //one of the lists below is used depending whether runLengthEncoded is true or false
    List<E> objectList;

    List<E> rleObjectList;
    IntArray rleCounts;

    int size = 0;
    final ObjectSerializer<E> objSerializer;


    //temporary fields used during the construction before serialisation - could be probably refactored into some builder which returns another object in the consolidate method    
    List<HashableByteArray> serializedObjectList;
    Map<HashableByteArray, Integer> valuemap;
    IntArray rleValues;
    IntArray enumValues;
    List<HashableByteArray> unique;

    int rawSize;
    int enumRawSize;
    int enumRleSize;

    boolean consolidated = false;
    /**
     * b
     * @param objSerializer
     * @param buildForSerialisation - is set to true at the construction and false at deserialisation
     */
    ObjectSegment(ObjectSerializer<E> objSerializer, boolean buildForSerialisation) {
        super(objSerializer.getFormatId());
        this.objSerializer = objSerializer;

        if(buildForSerialisation) {
            objectList = new ArrayList<E>();
            serializedObjectList = new ArrayList<HashableByteArray>();
            unique = new ArrayList<HashableByteArray>();
            valuemap = new HashMap<>();
            enumValues = new IntArray();
        } //else in the parseFrom will construct the necessary fields 
    }


    /**
     * add element to the end of the segment
     * 
     * @param e
     */
    public void add(E e) {
       
        byte[] b = objSerializer.serialize(e);
        HashableByteArray se = new HashableByteArray(b);
        int valueId; 
        if(valuemap.containsKey(se)) {
            valueId = valuemap.get(se);
            se = unique.get(valueId); //release the old se object to garbage
        } else {
            valueId = unique.size();
            valuemap.put(se, valueId);
            unique.add(se);
        }
        enumValues.add(valueId);
        serializedObjectList.add(se);
        objectList.add(e);
        size++;
    }


    public void add(int pos, E e) {
        if(pos==size) {
            add(e);
            return;
        }
        byte[] b = objSerializer.serialize(e);
        HashableByteArray se = new HashableByteArray(b);
        int valueId; 
        if(valuemap.containsKey(se)) {
            valueId = valuemap.get(se);
            se = unique.get(valueId); //release the old se object to garbage
        } else {
            valueId = unique.size();
            valuemap.put(se, valueId);
            unique.add(se);
        }
        enumValues.add(pos, valueId);
        serializedObjectList.add(pos, se);
        objectList.add(pos, e);
        size++;
    }

    @Override
    public void writeTo(ByteBuffer bb) {
        if(!consolidated) throw new IllegalStateException("The segment has to be consolidated before serialization can take place");

        boolean encoded = false;
        int position = bb.position();
        try {
            if(enumRleSize<=enumRawSize && enumRleSize<=rawSize) {
                encoded = writeEnumRle(bb);
            } else if(enumRawSize<enumRleSize && enumRawSize<=rawSize) {
                encoded = writeEnumFprof(bb);
            } 
        } catch (IndexOutOfBoundsException e) {
            encoded = false;
        }

        if(!encoded) {
            bb.position(position);
            writeRaw(bb);
        }
    }



    public void writeRaw(ByteBuffer bb) {
        bb.put(SUBFORMAT_ID_RAW);

        //write the size
        VarIntUtil.writeVarInt32(bb, objectList.size());
        //then write the values
        for(int i=0; i<size; i++) {
            byte[] b = serializedObjectList.get(i).b;
            VarIntUtil.writeVarInt32(bb, b.length);
            bb.put(b);
        }
    }

    boolean writeEnumFprof(ByteBuffer bb) {
        int position = bb.position();
        bb.put(SUBFORMAT_ID_ENUM_FPROF);
        //first write the enum values
        VarIntUtil.writeVarInt32(bb, unique.size());
        for(int i=0; i<unique.size(); i++) {
            byte[] b = unique.get(i).b;
            VarIntUtil.writeVarInt32(bb, b.length);
            bb.put(b);
        }

        //then writes the enum ids
        VarIntUtil.writeVarInt32(bb, size);
        
        FastPFOR128 fastpfor = FastPFORFactory.get();

        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int[] out = new int[size];
        int[] in = enumValues.array();
        fastpfor.compress(in, inputoffset, size, out, outputoffset);
        if (outputoffset.get() == 0) { 
            //fastpfor didn't compress anything, probably there were too few datapoints
            bb.put(position, SUBFORMAT_ID_ENUM_VB);
        } else {
            //write the fastpfor output
            for(int i=0; i<outputoffset.get(); i++) {
                bb.putInt(out[i]);
            }
        }
        //write the remaining bytes varint compressed
        for(int i = inputoffset.get(); i<size; i++) {
            VarIntUtil.writeVarInt32(bb, in[i]);
        }
        return true;
    }

    boolean writeEnumRle(ByteBuffer bb) {
        bb.put(SUBFORMAT_ID_ENUM_RLE);
        //first write the enum values
        VarIntUtil.writeVarInt32(bb, unique.size());
        for(int i=0; i<unique.size(); i++) {
            byte[] b = unique.get(i).b;
            VarIntUtil.writeVarInt32(bb, b.length);
            bb.put(b);
        }
        //then write the rleCounts
        VarIntUtil.writeVarInt32(bb, rleCounts.size());

        for(int i=0; i< rleCounts.size(); i++) {
            VarIntUtil.writeVarInt32(bb, rleCounts.get(i));
        }
        //and write the rleValues
        for(int i=0; i< rleCounts.size(); i++) {
            VarIntUtil.writeVarInt32(bb, rleValues.get(i));
        }
        return true;
    }



    protected void parse(ByteBuffer bb) throws DecodingException {
        byte formatId = bb.get();
        try {
            switch(formatId) {
            case SUBFORMAT_ID_RAW:
                parseRaw(bb);
                break;
            case SUBFORMAT_ID_ENUM_VB: //intentional fall trough
            case SUBFORMAT_ID_ENUM_FPROF://intentional fall trough
            case SUBFORMAT_ID_ENUM_RLE:
                parseEnum(formatId, bb);
                break;
            default:
                throw new DecodingException("Unknown subformatid: "+formatId);
            }
        } catch (DecodingException e) {
            throw e;
        } catch (Exception e) {
            throw new DecodingException("Cannot decode object segment subformatId "+formatId, e);
        }
    }

    private void parseRaw(ByteBuffer bb) throws DecodingException {
        size = VarIntUtil.readVarInt32(bb);
        objectList = new ArrayList<E>(size);
        for(int i = 0; i<size; i++) {
            int l = VarIntUtil.readVarInt32(bb);
            byte[] b = new byte[l];
            bb.get(b);
            E e = objSerializer.deserialize(b);
            objectList.add(e);
        }  
    }

    void parseEnum(int formatId, ByteBuffer bb) throws DecodingException {
        int n = VarIntUtil.readVarInt32(bb);
        List<E> uniqueValues = new ArrayList<E>();
        for(int i = 0;i<n; i++) {
            int l = VarIntUtil.readVarInt32(bb);
            byte[] b = new byte[l];
            bb.get(b);
            E e = objSerializer.deserialize(b);
            uniqueValues.add(e);
        }

        if(formatId == SUBFORMAT_ID_ENUM_RLE) {
            parseEnumRle(uniqueValues, bb);
        } else  {
            parseEnumNonRle(formatId, uniqueValues, bb);
        } 
    }

    private void parseEnumNonRle(int formatId, List<E> uniqueValues, ByteBuffer bb) throws DecodingException {
        size = VarIntUtil.readVarInt32(bb);
        int position = bb.position();
       
        int[] enumValues = new int[size];

        IntWrapper outputoffset = new IntWrapper(0);
        if(formatId==SUBFORMAT_ID_ENUM_FPROF) {
            int[] x = new int[(bb.limit()-position)/4];
            for(int i=0; i<x.length;i++) {
                x[i] = bb.getInt();
            }
            IntWrapper inputoffset = new IntWrapper(0);
            FastPFOR128 fastpfor = FastPFORFactory.get();
            fastpfor.uncompress(x, inputoffset, x.length, enumValues, outputoffset);
            bb.position(position+inputoffset.get()*4);
        }
        
        for(int i = outputoffset.get(); i<size;i++) {
            enumValues[i] = VarIntUtil.readVarInt32(bb);
        }
        objectList = new ArrayList<E>(size);
        for(int i =0 ; i<size; i++) {
            objectList.add(uniqueValues.get(enumValues[i]));
        }
    }


    private void parseEnumRle(List<E> uniqueValues, ByteBuffer bb ) throws DecodingException{ 
        int countNum = VarIntUtil.readVarInt32(bb);
        rleCounts = new IntArray(countNum);
        size = 0;
        for(int i=0; i<countNum; i++) {
            int c = VarIntUtil.readVarInt32(bb);
            rleCounts.add(c);
            size+=c;
        }
        rleObjectList = new ArrayList<>(countNum);

        for(int i=0; i<countNum; i++) {
            int c = VarIntUtil.readVarInt32(bb);
            rleObjectList.add(uniqueValues.get(c));
        }
        runLengthEncoded = true;
    }

    @Override
    public int getMaxSerializedSize() {
        return rawSize;
    }

    @Override
    public E[] getRange(int posStart, int posStop, boolean ascending) {
        if(posStart>=posStop) throw new IllegalArgumentException("posStart has to be smaller than posStop");
        if(runLengthEncoded) {
            if(ascending) {
                return getRleRangeAscending(posStart, posStop);
            } else {
                return getRleRangeDescending(posStart, posStop);
            }    
        } else {
            return getNonRleRange(posStart, posStop, ascending);
        }

    }
    E[] getNonRleRange(int posStart, int posStop, boolean ascending) {
        @SuppressWarnings("unchecked")
        E[] r = (E[]) Array.newInstance(objectList.get(0).getClass(), posStop-posStart);
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = objectList.get(i);
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = objectList.get(i);
            }
        }

        return r;
    }

    E[] getRleRangeAscending(int posStart, int posStop) {
        int n = posStop-posStart;
        @SuppressWarnings("unchecked")
        E[] r = (E[]) Array.newInstance(rleObjectList.get(0).getClass(), n);

        int k = posStart;
        int i = 0;
        while(k>=rleCounts.get(i)) {
            k-=rleCounts.get(i++);
        }
        int pos = 0;

        while(pos<n) {
            r[pos++] = rleObjectList.get(i);
            k++;
            if(k>=rleCounts.get(i)) {
                i++;
                k=0;
            }
        }
        return r;
    }





    public  E[] getRleRangeDescending(int posStart, int posStop) {
        if(posStop>=size) throw new IndexOutOfBoundsException("Index: "+posStop+" size: "+size);

        int n = posStop-posStart;
        @SuppressWarnings("unchecked")
        E[] r = (E[]) Array.newInstance(rleObjectList.get(0).getClass(), n);

        int k = size - posStop;
        int i = rleCounts.size()-1;
        while(k > rleCounts.get(i)) {
            k-=rleCounts.get(i--);
        }
        k=rleCounts.get(i)-k;

        int pos = 0;

        while(true) {
            r[pos++] = rleObjectList.get(i);
            if(pos==n) break;

            k--;
            if(k<0) {
                i--;
                k = rleCounts.get(i)-1;
            }
        }
        return r;
    }


    public E get(int index) {
        if(runLengthEncoded) {
            int k = 0;
            int i = 0;
            while(k<=index) {
                k += rleCounts.get(i);
                i++;
            }
            return rleObjectList.get(i-1);
        } else {
            return objectList.get(index);
        }
    }

    /**
     * the number of elements in this segment (not taking into account any compression due to run-length encoding)
     * @return
     */
    @Override
    public int size() {
        return size;
    }


    ObjectSegment<E> consolidate() {
        rleCounts = new IntArray();
        rleValues = new IntArray();
        
        rawSize = enumRawSize = enumRleSize = 1; //subFormatId byte
        
        rawSize += VarIntUtil.getEncodedSize(size);
        enumRawSize += VarIntUtil.getEncodedSize(size)+VarIntUtil.getEncodedSize(unique.size());
        enumRleSize += VarIntUtil.getEncodedSize(unique.size());
        
        for(int i=0; i<size; i++) {
            HashableByteArray se = serializedObjectList.get(i);
            byte[] b = se.b;
            int valueId = enumValues.get(i);
            rawSize+= VarIntUtil.getEncodedSize(b.length)+b.length;
            enumRawSize+=VarIntUtil.getEncodedSize(valueId);
            
            boolean rleAdded = false;
            int rleId = rleValues.size()-1;
            if(rleId>=0) {
                int lastValueId = rleValues.get(rleId);
                if(valueId == lastValueId) {
                    rleCounts.set(rleId, rleCounts.get(rleId)+1);
                    rleAdded = true;
                }
            }
            if(!rleAdded) {
                rleCounts.add(1);
                rleValues.add(valueId);
            }
        }

        for(int i = 0; i<unique.size(); i++) {
            HashableByteArray se = unique.get(i);
            byte[] b = se.b;
            int s = VarIntUtil.getEncodedSize(b.length)+b.length;
            enumRawSize+=s;
            enumRleSize+=s;
        }
       
        enumRleSize += VarIntUtil.getEncodedSize(rleCounts.size());
        for(int i =0 ;i<rleCounts.size();i++) {
            enumRleSize += VarIntUtil.getEncodedSize(rleCounts.get(i))+VarIntUtil.getEncodedSize(rleValues.get(i));
        }
        
        consolidated = true;
        return this;

    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ObjectSegment other = (ObjectSegment) obj;
        if (serializedObjectList == null) {
            if (other.serializedObjectList != null)
                return false;
        } else if (!serializedObjectList.equals(other.serializedObjectList))
            return false;
        return true;
    }

}

/**
 * wrapper around byte[] to allow it to be used in HashMaps
 */
class HashableByteArray {
    private int hash =0 ;
    final byte[] b;

    public HashableByteArray(byte[] b) {
        this.b = b ;
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = Arrays.hashCode(b);
        }            
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        HashableByteArray other = (HashableByteArray) obj;
        
        if(hashCode()!=other.hashCode()) 
            return false;
        
        if (!Arrays.equals(b, other.b))
            return false;
        return true;
    }


}

interface ObjectSerializer<E> {
    byte getFormatId();
    E deserialize(byte[] b) throws DecodingException;
    byte[] serialize(E e);
}
