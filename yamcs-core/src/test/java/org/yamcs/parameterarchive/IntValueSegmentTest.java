package org.yamcs.parameterarchive;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;


public class IntValueSegmentTest {
    @Test
    public void testShortNonRandom() throws IOException, DecodingException {
        int n =3;
        List<Value> l = new ArrayList<Value>(n);
        for(int i =0; i<n; i++) {
            l.add(ValueUtility.getSint32Value(100000+i));
        }
        IntValueSegment fvs = IntValueSegment.consolidate(l, true); 
        ByteBuffer bb = ByteBuffer.allocate(fvs.getMaxSerializedSize());
        fvs.writeTo(bb);
     
        assertEquals(IntValueSegment.SUBFORMAT_ID_DELTAZG_VB, bb.get(0)&0xF);
        bb.limit(bb.position());
        
        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);
        
        
        for(int i =0; i<n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }
    
    @Test
    public void testLongNonRandom() throws IOException, DecodingException {
        int n =1000;
        List<Value> l = new ArrayList<Value>(n);
        for(int i =0; i<n; i++) {
            l.add(ValueUtility.getUint32Value(100000+i));
        }
        IntValueSegment fvs = IntValueSegment.consolidate(l, false);
        ByteBuffer bb = ByteBuffer.allocate(fvs.getMaxSerializedSize());
        fvs.writeTo(bb);
        assertEquals(IntValueSegment.SUBFORMAT_ID_DELTAZG_FPF128_VB, bb.get(0)&0xF);
        
        bb.limit(bb.position());
        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);
        
        for(int i =0; i<n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }
    
    @Test
    public void testRandom() throws IOException, DecodingException {
        int n = 10;
        Random rand = new Random(0);
        List<Value> l = new ArrayList<Value>(n);
        for(int i =0; i<n; i++) {
            l.add(ValueUtility.getUint32Value(rand.nextInt()));
        }
        IntValueSegment fvs = IntValueSegment.consolidate(l, false);
        ByteBuffer bb = ByteBuffer.allocate(fvs.getMaxSerializedSize());
        fvs.writeTo(bb);
        assertEquals(IntValueSegment.SUBFORMAT_ID_RAW, bb.get(0)&0xF);
        
        //assertEquals(5, bb.position());
        bb.limit(bb.position());
        
        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);
        
        for(int i =0; i<n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }
}
