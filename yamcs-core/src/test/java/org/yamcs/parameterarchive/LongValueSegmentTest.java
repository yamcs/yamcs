package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class LongValueSegmentTest {
    @Test
    public void testUnsigned() throws IOException, DecodingException {
        LongValueSegment fvs = LongValueSegment.consolidate(Arrays.asList(ValueUtility.getUint64Value(1), ValueUtility.getUint64Value(2), 
                ValueUtility.getUint64Value(3)), Type.UINT64);
        assertEquals(28, fvs.getMaxSerializedSize());
        
        ByteBuffer bb = ByteBuffer.allocate(28);
        fvs.writeTo(bb);
        
        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);
        
        assertEquals(ValueUtility.getUint64Value(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getUint64Value(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getUint64Value(3), fvs1.getValue(2));
        
        assertArrayEquals(new long[]{1, 2,3}, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[]{3, 2}, fvs1.getRange(0, 2, false).getLongArray());
    }
    
    @Test
    public void testSigned() throws IOException, DecodingException {
        LongValueSegment fvs = LongValueSegment.consolidate(Arrays.asList(ValueUtility.getSint64Value(1), ValueUtility.getSint64Value(2), 
                ValueUtility.getSint64Value(3)), Type.SINT64);
        assertEquals(28, fvs.getMaxSerializedSize());
        
        ByteBuffer bb = ByteBuffer.allocate(28);
        fvs.writeTo(bb);
        
        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);
        
        assertEquals(ValueUtility.getSint64Value(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getSint64Value(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getSint64Value(3), fvs1.getValue(2));
        
        assertArrayEquals(new long[]{1, 2,3}, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[]{3, 2}, fvs1.getRange(0, 2, false).getLongArray());
    }
    
    @Test
    public void testTimestamp() throws IOException, DecodingException {
        LongValueSegment fvs = LongValueSegment.consolidate(Arrays.asList(ValueUtility.getTimestampValue(1), ValueUtility.getTimestampValue(2), 
                ValueUtility.getTimestampValue(3)), Type.TIMESTAMP);
        assertEquals(28, fvs.getMaxSerializedSize());
        
        ByteBuffer bb = ByteBuffer.allocate(28);
        fvs.writeTo(bb);
        
        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);
        
        assertEquals(ValueUtility.getTimestampValue(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getTimestampValue(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getTimestampValue(3), fvs1.getValue(2));
        
        assertArrayEquals(new long[]{1, 2,3}, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[]{3, 2}, fvs1.getRange(0, 2, false).getLongArray());
    }
}
