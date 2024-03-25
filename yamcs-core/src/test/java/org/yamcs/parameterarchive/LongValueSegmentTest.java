package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class LongValueSegmentTest {
    @Test
    public void testUnsigned() throws IOException, DecodingException {
        LongValueSegment lvs = new LongValueSegment(Type.UINT64);

        lvs.add(ValueUtility.getUint64Value(1));
        lvs.add(ValueUtility.getUint64Value(2));
        lvs.add(ValueUtility.getUint64Value(3));
        assertEquals(28, lvs.getMaxSerializedSize());

        ByteBuffer bb = ByteBuffer.allocate(28);
        lvs.writeTo(bb);

        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);

        assertEquals(ValueUtility.getUint64Value(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getUint64Value(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getUint64Value(3), fvs1.getValue(2));

        assertArrayEquals(new long[] { 1, 2, 3 }, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[] { 3, 2 }, fvs1.getRange(0, 2, false).getLongArray());
    }

    @Test
    public void testSigned() throws IOException, DecodingException {
        LongValueSegment lvs = new LongValueSegment(Type.SINT64);

        lvs.add(ValueUtility.getSint64Value(1));
        lvs.add(ValueUtility.getSint64Value(2));

        lvs.add(ValueUtility.getSint64Value(3));
        lvs.consolidate();

        assertEquals(28, lvs.getMaxSerializedSize());

        ByteBuffer bb = ByteBuffer.allocate(28);
        lvs.writeTo(bb);

        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);

        assertEquals(ValueUtility.getSint64Value(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getSint64Value(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getSint64Value(3), fvs1.getValue(2));

        assertArrayEquals(new long[] { 1, 2, 3 }, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[] { 3, 2 }, fvs1.getRange(0, 2, false).getLongArray());
    }

    @Test
    public void testTimestamp() throws IOException, DecodingException {
        LongValueSegment lvs = new LongValueSegment(Type.TIMESTAMP);

        lvs.add(ValueUtility.getTimestampValue(1));
        lvs.add(ValueUtility.getTimestampValue(2));

        lvs.add(ValueUtility.getTimestampValue(3));
        assertEquals(28, lvs.getMaxSerializedSize());

        ByteBuffer bb = ByteBuffer.allocate(28);
        lvs.writeTo(bb);

        bb.rewind();
        LongValueSegment fvs1 = LongValueSegment.parseFrom(bb);

        assertEquals(ValueUtility.getTimestampValue(1), fvs1.getValue(0));
        assertEquals(ValueUtility.getTimestampValue(2), fvs1.getValue(1));
        assertEquals(ValueUtility.getTimestampValue(3), fvs1.getValue(2));

        assertArrayEquals(new long[] { 1, 2, 3 }, fvs1.getRange(0, 3, true).getLongArray());
        assertArrayEquals(new long[] { 3, 2 }, fvs1.getRange(0, 2, false).getLongArray());
    }
}
