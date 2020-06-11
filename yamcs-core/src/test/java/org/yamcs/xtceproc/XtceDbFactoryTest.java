package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;
import org.yamcs.xtce.util.UnresolvedNameReference;

public class XtceDbFactoryTest {

    /*
     * This test constructs the following tree:
     * a [p1]
     * - b1 [p1]
     * - b2
     * - c1 [p1]
     * - b3
     * 
     */
    @Test
    public void testNamespaces() throws Exception {
        YConfiguration.setupTest("refmdb");
        XtceDbFactory.reset();

        ReferenceFinder refFinder = new ReferenceFinder(s ->{});
        Map<String, Object> m = new HashMap<>();
        m.put("type", "sheet");
        m.put("spec", "mdb/refmdb.xls");

        List<YConfiguration> mdbConfigs = Arrays.asList(YConfiguration.wrap(m));
        XtceDb db = XtceDbFactory.createInstance(mdbConfigs, true, true);

        SequenceContainer pkt1 = db.getSequenceContainer("/REFMDB/SUBSYS1/PKT1");
        assertNotNull(pkt1);
        // assertEquals(pkt1, db.getSequenceContainer("/REFMDB", "SUBSYS1/PKT1")); // Not supported yet
        assertEquals(pkt1, db.getSequenceContainer("/REFMDB/SUBSYS1", "PKT1"));

        Parameter p = db.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        assertNotNull(p);
        assertEquals(p, db.getParameter("/REFMDB/SUBSYS1", "IntegerPara1_1"));

        SpaceSystem ss = db.getSpaceSystem("/REFMDB/SUBSYS1");
        assertNotNull(ss);
        assertEquals(ss, db.getSpaceSystem("/REFMDB", "SUBSYS1"));

        FoundReference rr = refFinder.findReference(db.getRootSpaceSystem(),
                new UnresolvedNameReference("/REFMDB/SUBSYS1/IntegerPara1_1", Type.PARAMETER), ss);
        assertNotNull(rr);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1", rr.getNameDescription().getQualifiedName());

        rr = refFinder.findReference(db.getRootSpaceSystem(),
                new UnresolvedNameReference("../SUBSYS1/IntegerPara1_1", Type.PARAMETER), ss);
        assertNotNull(rr);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1", rr.getNameDescription().getQualifiedName());
    }

   
}
