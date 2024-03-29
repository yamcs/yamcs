package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class FloatValueSegmentTest {
    @Test
    public void test() throws IOException, DecodingException {
        
        FloatValueSegment fvs = new FloatValueSegment();
        fvs.add(ValueUtility.getFloatValue(1.2f));
        
        fvs.add(ValueUtility.getFloatValue(2.3f));
        fvs.add(ValueUtility.getFloatValue((float) 3));
        fvs.consolidate();

        assertEquals(18, fvs.getMaxSerializedSize());

        ByteBuffer bb = ByteBuffer.allocate(24);
        fvs.writeTo(bb);

        bb.rewind();
        FloatValueSegment fvs1 = FloatValueSegment.parseFrom(bb);

        assertEquals(ValueUtility.getFloatValue(1.2f), fvs1.getValue(0));
        assertEquals(ValueUtility.getFloatValue(2.3f), fvs1.getValue(1));
        assertEquals(ValueUtility.getFloatValue(3f), fvs1.getValue(2));

        assertArrayEquals(new float[] { 1.2f, 2.3f, 3 }, fvs1.getRange(0, 3, true).getFloatArray(), 1e-10f);
        assertArrayEquals(new float[] { 3, 2.3f }, fvs1.getRange(0, 2, false).getFloatArray(), 1e-10f);
    }

    @Test
    public void test2() throws IOException, DecodingException {
        FloatValueSegment fvs = new FloatValueSegment();
        List<Value> values = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            Value v = ValueUtility.getFloatValue(1.2f);
            values.add(v);
            fvs.add(v);
        }
        fvs.consolidate();

        ByteBuffer bb = ByteBuffer.allocate(fvs.getMaxSerializedSize());
        fvs.writeTo(bb);
        int length = bb.position();
        ByteBuffer bb1 = ByteBuffer.allocate(length);
        bb1.put(bb.array(), 0, length);

        bb1.rewind();
        FloatValueSegment fvs1 = FloatValueSegment.parseFrom(bb1);
        assertEquals(200, fvs1.size());
    }
}
