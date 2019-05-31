package org.yamcs.xtce;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.xtce.util.DataTypeUtil;

public class PathElementTest {
    
    @Test
    public void test1() {
        PathElement pe = PathElement.fromString("aa[1][2][3]");
        assertEquals("aa", pe.name);
        assertArrayEquals(new int[] {1, 2, 3}, pe.index);
        
    }
    
    @Test
    public void test2() {
        PathElement pe = PathElement.fromString("abc");
        assertEquals("abc", pe.name);
        assertNull(pe.index);
    }
    
    
    @Test
    public void test3() {
        IntegerParameterType intType =   new IntegerParameterType("m1type");
        AggregateParameterType aggType = new AggregateParameterType("aggType");
        Member m1 = new Member("m1");
        m1.setDataType(intType);
        aggType.addMember(m1);
        ArrayParameterType arrayType = new ArrayParameterType("test", 1);
        arrayType.setElementType(aggType);
       // PathElement[] path = new PathElement[] {new PathElement("m1", new int[] {2})};
        PathElement[] path = new PathElement[] {PathElement.fromString("[3]"), PathElement.fromString("m1")};
        DataType dt = DataTypeUtil.getMemberType(arrayType, path);
        
        
        assertEquals(intType, dt);
    }
}
