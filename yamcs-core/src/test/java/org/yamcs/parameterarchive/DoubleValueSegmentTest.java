package org.yamcs.parameterarchive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class DoubleValueSegmentTest {
    @Test
    public void test() throws IOException, DecodingException {
        DoubleValueSegment fvs = DoubleValueSegment.consolidate(Arrays.asList(ValueUtility.getDoubleValue(1.2), ValueUtility.getDoubleValue(2.3), ValueUtility.getDoubleValue(3)));
        assertEquals(28, fvs.getMaxSerializedSize());
        
        ByteBuffer bb = ByteBuffer.allocate(28);
        fvs.writeTo(bb);
        
        bb.rewind();
        DoubleValueSegment fvs1 = DoubleValueSegment.parseFrom(bb);
        
        assertEquals(ValueUtility.getDoubleValue(1.2), fvs1.getValue(0));
        assertEquals(ValueUtility.getDoubleValue(2.3), fvs1.getValue(1));
        assertEquals(ValueUtility.getDoubleValue(3), fvs1.getValue(2));

        assertArrayEquals(new double[]{1.2, 2.3,3}, fvs1.getRange(0, 3, true), 1e-10);
        assertArrayEquals(new double[]{3, 2.3}, fvs1.getRange(0, 2, false), 1e-10);
    }
}
