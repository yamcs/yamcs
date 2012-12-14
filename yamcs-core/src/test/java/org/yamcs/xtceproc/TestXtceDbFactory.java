package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NameReference;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SpaceSystem;


public class TestXtceDbFactory {

    /* This test constructs the following tree:
     * a [p1]
     *   - b1 [p1]
     *   - b2 
     *       - c1 [p1]
     *   - b3
     * 
     */
    
    @Test
    public void testResolveReference() {
        SpaceSystem a=new SpaceSystem("a");
        SpaceSystem a_b1=new SpaceSystem("b1");
        a.addSpaceSystem(a_b1);
        SpaceSystem a_b2=new SpaceSystem("b2");
        a.addSpaceSystem(a_b2);
        SpaceSystem a_b3=new SpaceSystem("b3");
        a.addSpaceSystem(a_b3);
        
        SpaceSystem a_b2_c1=new SpaceSystem("c1");
        a_b2.addSpaceSystem(a_b2_c1);
       
        Parameter a_p1=new Parameter("p1");
        a_p1.addAlias("MDB:OPS Name", "a_p1");
        a.addParameter(a_p1);
        
        Parameter a_b1_p1=new Parameter("p1");
        a_b1_p1.addAlias("MDB:OPS Name", "a_b1_p1");
        a_b1.addParameter(a_b1_p1);
        
        Parameter a_b2_c1_p1=new Parameter("p1");
        a_b2_c1_p1.addAlias("MDB:OPS Name", "a_b2_c1_p1");
        a_b2_c1.addParameter(a_b2_c1_p1);
        
        NameDescription nd;
        
        nd=XtceDbFactory.findReference(a, new NameReference("p1", Type.PARAMETER, null), a_b2_c1);
        assertEquals(nd, a_b2_c1_p1);
        
      
        
        nd=XtceDbFactory.findReference(a, new NameReference("p1", Type.PARAMETER, null), a_b2);
        assertEquals(a_p1, nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("p1", Type.PARAMETER, null), a_b1);
        assertEquals(a_b1_p1, nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("b2/c1/p1", Type.PARAMETER, null), a_b2_c1);
        assertEquals(a_b2_c1_p1, nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("b2/.//../b2/c1/p1", Type.PARAMETER, null), a_b2_c1);
        assertEquals(a_b2_c1_p1, nd);
        
        
        nd=XtceDbFactory.findReference(a, new NameReference("../p1", Type.PARAMETER, null), a_b2_c1);
        assertNull(nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("../../p1", Type.PARAMETER, null), a_b2_c1);
        assertEquals(a_p1, nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("../../p1", Type.PARAMETER, null), a_b3);
        assertNull(nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("./p1", Type.PARAMETER, null), a_b2_c1);
        assertEquals(nd, a_b2_c1_p1);
        
        nd=XtceDbFactory.findReference(a, new NameReference("./p1", Type.PARAMETER, null), a_b2);
        assertNull(nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("/a/b2/c1/.//p1", Type.PARAMETER, null), a_b2);
        assertEquals(a_b2_c1_p1, nd);
        
        
        nd=XtceDbFactory.findReference(a, new NameReference("/a/..", Type.PARAMETER, null), a_b2);
        assertNull(nd);
        
        nd=XtceDbFactory.findReference(a, new NameReference("p2", Type.PARAMETER, null), a_b2);
        assertNull(nd);
    }
}
