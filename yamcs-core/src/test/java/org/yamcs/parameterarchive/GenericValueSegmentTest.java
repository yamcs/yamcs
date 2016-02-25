package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;

public class GenericValueSegmentTest {
    @Test
    public void test1() throws Exception {
        Value v1 = ValueUtility.getSint32Value(3);
        Value v2 = ValueUtility.getSint32Value(30);
        Value v3 = ValueUtility.getUint32Value(3);

        GenericValueSegment gvs = new GenericValueSegment();
        gvs.add(0, v1);
        gvs.add(1, v2);
        gvs.add(2, v3);

        ByteBuffer bb = ByteBuffer.allocate(gvs.getMaxSerializedSize());
        gvs.writeTo(bb);
        
        bb.rewind();
        GenericValueSegment gvs1 = GenericValueSegment.parseFrom(bb);

        assertEquals(3, gvs1.values.size());
        assertEquals(v1, gvs1.values.get(0));
        assertEquals(v2, gvs1.values.get(1));
        assertEquals(v3, gvs1.values.get(2));
    }
}
