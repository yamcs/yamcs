package org.yamcs.time;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.InitException;

public class TimeOfFlightEstimatorTest {
    
    @Test
    public void testInterval() throws InitException {
        TimeOfFlightEstimator tofe = new TimeOfFlightEstimator("test", "test", false);
        tofe.addDataPoint(Instant.get(1000, 0), Instant.get(100000, 0), new double[] {1, 0.5});
        assertEquals(1.0, tofe.getTof(Instant.get(1000)), 1e-10);
        assertEquals(1.5, tofe.getTof(Instant.get(2000)), 1e-10);
        
        assertTrue(Double.isNaN(tofe.getTof(Instant.get(100001))));
        
        
        tofe.addDataPoint(Instant.get(100000, 0), Instant.get(200000, 0), new double[] {1, 0.2});
        assertEquals(1.0002, tofe.getTof(Instant.get(100001)), 1e-10);
    }
}
