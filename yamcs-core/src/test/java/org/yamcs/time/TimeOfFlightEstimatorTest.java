package org.yamcs.time;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.yamcs.YConfiguration;

public class TimeOfFlightEstimatorTest {
    
    YConfiguration getConfig(double deftof) {
        Map<String, Object>  m = new HashMap<>();
        m.put("defaultTof", deftof);
        
        return YConfiguration.wrap(m);
    }
    
    @Test
    public void testDefault1() {
        YConfiguration conf = getConfig(3.14);
        TimeOfFlightEstimator tofe = new TimeOfFlightEstimator(conf);
        assertEquals(3.14, tofe.getTof(Instant.get(1999)), 1e-10);
    }
    @Test
    public void testDefault2() {
        TimeOfFlightEstimator tofe = new TimeOfFlightEstimator(YConfiguration.emptyConfig());
        assertEquals(0, tofe.getTof(Instant.get(1999)), 1e-10);
    }
    
    @Test
    public void testInterval() {
        YConfiguration conf = getConfig(3.14);
        TimeOfFlightEstimator tofe = new TimeOfFlightEstimator(conf);
        tofe.addDataPoint(Instant.get(1000, 0), Instant.get(100000, 0), new double[] {1, 0.5});
        assertEquals(1.0, tofe.getTof(Instant.get(1000)), 1e-10);
        assertEquals(1.5, tofe.getTof(Instant.get(2000)), 1e-10);
        
        assertEquals(3.14, tofe.getTof(Instant.get(100001)), 1e-10);
        
        
        tofe.addDataPoint(Instant.get(100000, 0), Instant.get(200000, 0), new double[] {1, 0.2});
        assertEquals(1.0002, tofe.getTof(Instant.get(100001)), 1e-10);
    }
}
