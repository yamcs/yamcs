package org.yamcs.utils;


import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;



public class StringConverter {
    static final BigInteger B64 = BigInteger.ZERO.setBit(64);


    public static String toString(Value rv) {
        switch(rv.getType()) {
        case BINARY:
            return "(BINARY)"+byteBufferToHexString(rv.getBinaryValue().asReadOnlyByteBuffer());
        case DOUBLE:
            return "(DOUBLE)"+rv.getDoubleValue();
        case FLOAT:
            return "(FLOAT)"+rv.getFloatValue();
        case SINT32:
            return "(SIGNED_INTEGER)"+rv.getSint32Value();
        case UINT32:
            return "(UNSIGNED_INTEGER)"+Long.toString(rv.getUint32Value()&0xFFFFFFFFL);
        case SINT64:
            return "(SIGNED_INTEGER)"+rv.getSint64Value();
        case UINT64:
            return "(UNSIGNED_INTEGER)"+rv.getUint64Value();
        case STRING:
            return "(STRING)"+rv.getStringValue();
        case BOOLEAN:
            return "(BOOLEAN)"+rv.getBooleanValue();
        case TIMESTAMP:
            return "(TIMESTAMP)"+TimeEncoding.toOrdinalDateTime(rv.getTimestampValue());
        }
        return null;
    }

    public static String toString(Value rv, boolean withType) {
        if(withType)return toString(rv);
        switch(rv.getType()) {
        case BINARY:
            return byteBufferToHexString(rv.getBinaryValue().asReadOnlyByteBuffer());
        case DOUBLE:
            return Double.toString(rv.getDoubleValue());
        case FLOAT:
            return Float.toString(rv.getFloatValue());
        case SINT32:
            return Integer.toString(rv.getSint32Value());
        case UINT32:
            return Long.toString(rv.getUint32Value()&0xFFFFFFFFL);
        case SINT64:
            return Long.toString(rv.getSint64Value());
        case UINT64:
            if (rv.getUint64Value() >= 0)
                return Long.toString(rv.getUint64Value());
            else
                return BigInteger.valueOf(rv.getUint64Value()).add(B64).toString();
        case STRING:
            return rv.getStringValue();
        case BOOLEAN:
            return Boolean.toString(rv.getBooleanValue());
        case TIMESTAMP:
            return TimeEncoding.toOrdinalDateTime(rv.getTimestampValue());

        }
        return null;
    }


    public static String arrayToHexString(byte[] b){
        StringBuilder sb =new StringBuilder();
        for (int i=0;i<b.length;i++) {
            String s=Integer.toString(b[i]&0xFF,16);
            if(s.length()==1) s="0"+s;
            sb.append(s.toUpperCase());
        }
        return sb.toString();
    }

    public static String byteBufferToHexString(ByteBuffer bb) {
        bb.mark();
        StringBuilder sb =new StringBuilder();
        int offset=0;
        while(bb.hasRemaining()) {
            if(offset%33==0)sb.append("\n");
            String s=Integer.toString(bb.get()&0xFF,16);
            offset++;
            if(s.length()==1) sb.append("0");
            sb.append(s.toUpperCase());
        }
        bb.reset();
        return sb.toString();
    }  
    
    
    /**
     * Convert a hex string into a byte array. No check is done if the string has an even number of
     * hex digits. The last one is ignored in case the number is odd.
     * @param s
     * @return
     */
    public static byte[] hexStringToArray(String s) {
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<s.length()/2;i++) {
            b[i]=(byte)(Integer.parseInt(s.substring(2*i,2*i+2),16)&0xFF);
        }
        return b;
    }
    
    
    /**
     * Convert a NamedObjectId to a pretty string for use in log messages etc. This gives a
     * better formatting than the default protobuf-generated toString.
     */
    public static String idToString(NamedObjectId id) {
        if (id == null) return "null";
        if (id.hasNamespace()) {
            return "'" + id.getName() + "' (namespace: '" + id.getNamespace() + "')";
        } else {
            return "'" + id.getName() + "' (no namespace)";
        }
    }
    
    /**
     * Convert a list of NamedObjectId to a pretty string for use in log messages etc. This gives a
     * better formatting than the default protobuf-generated toString.
     */
    public static String idListToString(List<NamedObjectId> idList) {
        if (idList == null) return "null";
        StringBuilder buf = new StringBuilder("[");
        boolean first = true;
        for (NamedObjectId id : idList) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(idToString(id));
        }
        return buf.append("]").toString();
    }
}
