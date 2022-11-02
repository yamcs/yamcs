package org.yamcs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.InitException;
import org.yamcs.utils.TimeEncoding;

public class TimeOfFlightEstimatorTest {

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testInterval() throws InitException {
        TimeOfFlightEstimator tofe = new TimeOfFlightEstimator("test", "test", false);
        tofe.addInterval(Instant.get(1000, 0), Instant.get(100000, 0), new double[] { 1, 0.5 });
        assertEquals(1.0, tofe.getTof(Instant.get(1000)), 1e-10);
        assertEquals(1.5, tofe.getTof(Instant.get(2000)), 1e-10);

        assertTrue(Double.isNaN(tofe.getTof(Instant.get(100001))));

        tofe.addInterval(Instant.get(100000, 0), Instant.get(200000, 0), new double[] { 1, 0.2 });
        assertEquals(1.0002, tofe.getTof(Instant.get(100001)), 1e-10);
    }

    @Test
    public void testRetrievalFromArchive() throws Exception {
        Instant t0 = TimeEncoding.getWallclockHresTime().plus(100);
        Instant t1 = t0.plus(1000);

        TimeOfFlightEstimator tofe1 = new TimeOfFlightEstimator("test", "test", true);
        tofe1.addInterval(t0, t1, new double[] { 1, 0.2 });

        TimeOfFlightEstimator tofe2 = new TimeOfFlightEstimator("test", "test", true);
        assertEquals(1.0002, tofe2.getTof(t0.plus(0.001)), 1e-10);
    }
}
