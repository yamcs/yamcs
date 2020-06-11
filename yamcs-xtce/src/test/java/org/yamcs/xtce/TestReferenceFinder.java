package org.yamcs.xtce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.yamcs.xtce.util.UnresolvedNameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;

public class TestReferenceFinder {

    @Test
    public void testResolveReference() {
        ReferenceFinder refFinder = new ReferenceFinder(s ->{});
        SpaceSystem root = new SpaceSystem("");

        SpaceSystem a = new SpaceSystem("a");
        root.addSpaceSystem(a);

        SpaceSystem a_b1 = new SpaceSystem("b1");
        a.addSpaceSystem(a_b1);
        SpaceSystem a_b2 = new SpaceSystem("b2");
        a.addSpaceSystem(a_b2);
        SpaceSystem a_b3 = new SpaceSystem("b3");
        a.addSpaceSystem(a_b3);

        SpaceSystem a_b2_c1 = new SpaceSystem("c1");
        a_b2.addSpaceSystem(a_b2_c1);

        Parameter a_p1 = new Parameter("p1");
        a_p1.addAlias("MDB:OPS Name", "a_p1");
        a.addParameter(a_p1);

        Parameter a_b1_p1 = new Parameter("p1");
        a_b1_p1.addAlias("MDB:OPS Name", "a_b1_p1");
        a_b1.addParameter(a_b1_p1);

        Parameter a_b2_c1_p1 = new Parameter("p1");
        a_b2_c1.addParameter(a_b2_c1_p1);

        FoundReference rr;

        rr = refFinder.findReference(root, new UnresolvedNameReference("/a/b2/c1/p1", Type.PARAMETER), a_b1);
        assertEquals(a_b2_c1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("p1", Type.PARAMETER), a_b2_c1);
        assertEquals(a_b2_c1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("p1", Type.PARAMETER), a_b2);
        assertEquals(a_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("p1", Type.PARAMETER), a_b1);
        assertEquals(a_b1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("b2/c1/p1", Type.PARAMETER), a_b2_c1);
        assertEquals(a_b2_c1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("b2/.//../b2/c1/p1", Type.PARAMETER),
                a_b2_c1);
        assertEquals(a_b2_c1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("../p1", Type.PARAMETER), a_b2_c1);
        assertNull(rr);

        rr = refFinder.findReference(root, new UnresolvedNameReference("../../p1", Type.PARAMETER), a_b2_c1);
        assertEquals(a_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("../../p1", Type.PARAMETER), a_b3);
        assertNull(rr);

        rr = refFinder.findReference(root, new UnresolvedNameReference("./p1", Type.PARAMETER), a_b2_c1);
        assertEquals(rr.getNameDescription(), a_b2_c1_p1);

        rr = refFinder.findReference(root, new UnresolvedNameReference("./p1", Type.PARAMETER), a_b2);
        assertNull(rr);

        rr = refFinder.findReference(root, new UnresolvedNameReference("/a/b2/c1/.//p1", Type.PARAMETER), a_b2);
        assertEquals(a_b2_c1_p1, rr.getNameDescription());

        rr = refFinder.findReference(root, new UnresolvedNameReference("/a/..", Type.PARAMETER), a_b2);
        assertNull(rr);

        rr = refFinder.findReference(root, new UnresolvedNameReference("p2", Type.PARAMETER), a_b2);
        assertNull(rr);
    }
}
