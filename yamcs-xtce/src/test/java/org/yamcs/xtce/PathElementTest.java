package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.util.DataTypeUtil;

public class PathElementTest {

    @Test
    public void test1() {
        PathElement pe = PathElement.fromString("aa[1][2][3]");
        assertEquals("aa", pe.name);
        assertArrayEquals(new int[] { 1, 2, 3 }, pe.index);
    }

    @Test
    public void test2() {
        PathElement pe = PathElement.fromString("abc");
        assertEquals("abc", pe.name);
        assertNull(pe.index);
    }

    @Test
    public void test3() {
        IntegerParameterType.Builder intTypeb = new IntegerParameterType.Builder();
        intTypeb.setName("m1type");
        AggregateParameterType.Builder aggType = new AggregateParameterType.Builder();
        aggType.setName("aggType");
        Member m1 = new Member("m1");
        IntegerParameterType intType = intTypeb.build();

        m1.setDataType(intType);

        aggType.addMember(m1);
        ArrayParameterType.Builder arrayType = new ArrayParameterType.Builder();
        arrayType.setName("test");
        arrayType.setNumberOfDimensions(1);
        arrayType.setElementType(aggType.build());

        PathElement[] path = new PathElement[] { PathElement.fromString("[3]"), PathElement.fromString("m1") };
        DataType dt = DataTypeUtil.getMemberType(arrayType.build(), path);

        assertEquals(intType, dt);
    }
}
