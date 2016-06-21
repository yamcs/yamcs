package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class BooleanValueSegmentTest {

    @Test
    public void test() throws IOException, DecodingException {
        BooleanValueSegment bvs = BooleanValueSegment.consolidate(Arrays.asList(ValueUtility.getBooleanValue(true), ValueUtility.getBooleanValue(true), ValueUtility.getBooleanValue(false)));
        assertEquals(16, bvs.getMaxSerializedSize());
        assertEquals(3, bvs.size());
        
        ByteBuffer bb = ByteBuffer.allocate(12);
        bvs.writeTo(bb);
        
        bb.rewind();
        BooleanValueSegment bvs1 = BooleanValueSegment.parseFrom(bb);
        
        assertEquals(ValueUtility.getBooleanValue(true), bvs1.getValue(0));
        assertEquals(ValueUtility.getBooleanValue(true), bvs1.getValue(1));
        assertEquals(ValueUtility.getBooleanValue(false), bvs1.getValue(2));
    }

}
