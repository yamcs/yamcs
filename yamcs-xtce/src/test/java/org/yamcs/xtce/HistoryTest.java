package org.yamcs.xtce;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class HistoryTest {
    
    @Test
    public void testVersionCompare() {
        History h1 = new History("0.1 Draft", "01-05-2020", "abc");
        History h2 = new History("0.2", "01-05-2020", "abc");
        History h3 = new History("0.9", "01-05-2020", "abc");
        History h4 = new History("0.10", "01-05-2020", "abc");
        History h5 = new History("0.10.2", "01-05-2020", "abc");
        
        History[] arr = new History[] { h3, h5, h1, h2, h4};
        Arrays.sort(arr);
        assertEquals(h1.getVersion(), arr[0].getVersion());
        assertEquals(h2.getVersion(), arr[1].getVersion());
        assertEquals(h3.getVersion(), arr[2].getVersion());
        assertEquals(h4.getVersion(), arr[3].getVersion());
        assertEquals(h5.getVersion(), arr[4].getVersion());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidVersion() {
        // don't accept non-standard versions, since our comparator can't deal with it
        new History("v0.8", "01-05-2020", "abc");
    }
}
