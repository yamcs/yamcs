package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class ValueArrayTest {

    @Test
    public void testInt() {
        ValueArray va0 = new ValueArray(Type.UINT32, new int[] { 1, 3 });
        ValueArray va1 = new ValueArray(Type.UINT32, new int[] { 2 });

        ValueArray merged = ValueArray.merge(new int[] { 0, 1, 0 }, va0, va1);
        assertArrayEquals(new int[] { 1, 2, 3 }, merged.getIntArray());
    }
}
