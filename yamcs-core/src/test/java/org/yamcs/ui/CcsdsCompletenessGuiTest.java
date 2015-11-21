package org.yamcs.ui;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.yamcs.ui.CcsdsCompletenessGui.Gap;

public class CcsdsCompletenessGuiTest {
    
    private void testEquals(String name, long start, long end, Gap g) {
        assertEquals(name, g.name);
        assertEquals(start, g.start);
        assertEquals(end, g.end);
    }
    
    @Test
    public void testConsolidate() {
        List<Gap> list = new ArrayList<Gap>();
        Gap g1 = new Gap("x", -1, 10, -1,-1); //seqStart and seqEnd are not used
        Gap g2 = new Gap("x", 20, 22, -1, -1);
        Gap g3 = new Gap("x", 24, 26, -1, -1);
        Gap g4 = new Gap("x", 40, 60, -1, -1);
        Gap g5 = new Gap("x", 80, -1, -1, -1);
        Gap gy1 = new Gap("y", 20,25, -1,-1);
        list.add(g1);list.add(g2);list.add(gy1);list.add(g3);list.add(g4); list.add(g5);
        
        List<Gap> clist = CcsdsCompletenessGui.consolidate(list, 3 ,5 );
        
        assertEquals(6, clist.size());
        testEquals("x", 5,10, clist.get(0));
        testEquals("x", 20, 26, clist.get(1));
        testEquals("y", 20, 25, clist.get(2));
        testEquals("x", 40, 45, clist.get(3));
        testEquals("x", 55, 60, clist.get(4));
        testEquals("x", 80, 85, clist.get(5));
        
    }
}
