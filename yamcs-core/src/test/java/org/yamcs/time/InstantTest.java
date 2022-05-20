package org.yamcs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class InstantTest {
    @Test
    public void test1() {
        Instant t = Instant.get(100, Integer.MAX_VALUE);
        assertInstantEquals(t, 102, 147483647);
    }

    @Test
    public void test2() {
        Instant t1 = Instant.get(100, 20);
        Instant t2 = Instant.get(100, 20);
        Instant t3 = t1.plus(t2);
        assertInstantEquals(t3, 200, 40);
    }

    @Test
    public void test3() {
        Instant t1 = Instant.get(100, 20);
        Instant t2 = Instant.get(100, 1000_000_000 - 20);
        Instant t3 = t1.plus(t2);
        assertInstantEquals(t3, 201, 0);
    }

    @Test
    public void testPlusSec1() {
        Instant t1 = Instant.get(100, 20);
        Instant t2 = t1.plus(1e-3 + 5e-12);
        assertInstantEquals(t2, 101, 25);
    }

    @Test
    public void testMinusSec() {
        Instant t1 = Instant.get(100, 20);
        Instant t2 = t1.plus(-5e-12);
        assertInstantEquals(t2, 100, 15);

        Instant t3 = t1.plus(-25e-12);
        assertInstantEquals(t3, 99, 999_999_995);

    }

    @Test
    public void test5() {
        assertThrows(IllegalArgumentException.class, () -> {
            Instant t1 = Instant.get(100, 20);
            t1.plus(1e50);
        });
    }

    @Test
    public void testDeltaFrom() {
        Instant t1 = Instant.get(100, 20);
        Instant t2 = Instant.get(100, 30);

        double d = t2.deltaFrom(t1);
        assertEquals(1e-11, d, 1e-20);

        Instant t3 = Instant.get(101, 30);
        d = t3.deltaFrom(t1);
        assertEquals(1e-3 + 1e-11, d, 1e-20);

    }

    private void assertInstantEquals(Instant t, long expectedMillis, int expectedPicos) {
        assertEquals(expectedMillis, t.getMillis());
        assertEquals(expectedPicos, t.getPicos());
    }
}
