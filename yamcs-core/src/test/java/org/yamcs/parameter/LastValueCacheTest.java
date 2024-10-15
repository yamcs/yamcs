package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.LastValueCache.ParamBuffer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;

public class LastValueCacheTest {
    static Parameter p0, p1;
    static ParameterValue p0v0, p1v0, p1v1, p1v2, p1v3;

    @BeforeAll
    static public void beforeTest() {
        TimeEncoding.setUp();

        p0 = new Parameter("p0");
        p0.setDataSource(DataSource.CONSTANT);
        p0v0 = new ParameterValue(p0);

        p1 = new Parameter("p1");
        p1v0 = new ParameterValue(p1);
        p1v1 = new ParameterValue(p1);
        p1v2 = new ParameterValue(p1);
        p1v3 = new ParameterValue(p1);
    }

    @Test
    public void test0() {
        assertThrows(IllegalArgumentException.class, () -> {
            LastValueCache lvc = new LastValueCache(Arrays.asList(p0v0));
            lvc.enableBuffering(p0, 3);
        });
    }

    @Test
    public void testPb1() {
        ParamBuffer pb = new ParamBuffer(3);

        pb.add(p1v0);
        assertEquals(p1v0, pb.nthFromEnd(0));
        assertNull(pb.nthFromEnd(1));

        pb.add(p1v1);
        assertEquals(p1v1, pb.nthFromEnd(0));
        assertEquals(p1v0, pb.nthFromEnd(1));

    }

    @Test
    public void test1() {
        LastValueCache lvc = new LastValueCache(Arrays.asList(p0v0));
        assertEquals(1, lvc.size());
        assertEquals(p0v0, lvc.getValue(p0));

        lvc.add(p1v0);
        assertEquals(2, lvc.size());

        assertEquals(p1v0, lvc.getValue(p1));

        lvc.add(p1v1);
        assertEquals(2, lvc.size());
        assertEquals(p1v1, lvc.getValue(p1));

    }

    @Test
    public void test2() {
        assertThrows(IllegalStateException.class, () -> {
            LastValueCache lvc = new LastValueCache(Arrays.asList(p0v0));
            lvc.getValueFromEnd(p1, 1);
        });
    }

    @Test
    public void test3() {
        assertThrows(IllegalArgumentException.class, () -> {
            LastValueCache lvc = new LastValueCache(Arrays.asList(p0v0));
            lvc.enableBuffering(p1, 2);
            lvc.getValueFromEnd(p1, -2);
        });
    }

    @Test
    public void test4() {
        LastValueCache lvc = new LastValueCache(Arrays.asList(p0v0));
        lvc.enableBuffering(p1, 3);
        assertNull(lvc.getValueFromEnd(p1, 2));

        lvc.add(p1v0);
        assertEquals(p1v0, lvc.getValue(p1));
        assertEquals(p1v0, lvc.getValueFromEnd(p1, 0));
        assertNull(lvc.getValueFromEnd(p1, 2));

        lvc.add(p1v1);
        lvc.add(p1v2);
        assertEquals(p1v2, lvc.getValueFromEnd(p1, 0));
        assertEquals(p1v1, lvc.getValueFromEnd(p1, 1));
        assertEquals(p1v0, lvc.getValueFromEnd(p1, 2));

        lvc.enableBuffering(p1, 4);
        lvc.add(p1v3);
        assertEquals(p1v3, lvc.getValueFromEnd(p1, 0));
        assertEquals(p1v2, lvc.getValueFromEnd(p1, 1));
        assertEquals(p1v1, lvc.getValueFromEnd(p1, 2));
        assertEquals(p1v0, lvc.getValueFromEnd(p1, 3));
    }
}
