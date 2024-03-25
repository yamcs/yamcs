package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class DoubleValueSegmentTest {
    @Test
    public void test() throws IOException, DecodingException {
        DoubleValueSegment dvs = new DoubleValueSegment();

        dvs.add(ValueUtility.getDoubleValue(1.2));
        dvs.add(ValueUtility.getDoubleValue(2.3));
        dvs.add(ValueUtility.getDoubleValue(3));
        dvs.consolidate();

        assertEquals(28, dvs.getMaxSerializedSize());

        ByteBuffer bb = ByteBuffer.allocate(28);
        dvs.writeTo(bb);

        bb.rewind();
        DoubleValueSegment dvs1 = DoubleValueSegment.parseFrom(bb);

        assertEquals(ValueUtility.getDoubleValue(1.2), dvs1.getValue(0));
        assertEquals(ValueUtility.getDoubleValue(2.3), dvs1.getValue(1));
        assertEquals(ValueUtility.getDoubleValue(3), dvs1.getValue(2));

        assertArrayEquals(new double[] { 1.2, 2.3, 3 }, dvs1.getRange(0, 3, true).getDoubleArray(), 1e-10);
        assertArrayEquals(new double[] { 3, 2.3 }, dvs1.getRange(0, 2, false).getDoubleArray(), 1e-10);
    }
}
