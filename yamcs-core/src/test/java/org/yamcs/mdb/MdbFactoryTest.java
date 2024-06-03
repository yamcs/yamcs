package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;

public class MdbFactoryTest {

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
        MdbFactory.reset();

        ReferenceFinder refFinder = new ReferenceFinder(s -> {
        });
        Map<String, Object> m = new HashMap<>();
        m.put("type", "sheet");
        m.put("spec", "mdb/refmdb.xls");

        List<YConfiguration> mdbConfigs = Arrays.asList(YConfiguration.wrap(m));
        Mdb mdb = MdbFactory.createInstance(mdbConfigs, true, true);

        SequenceContainer pkt1 = mdb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1");
        assertNotNull(pkt1);
        // assertEquals(pkt1, db.getSequenceContainer("/REFMDB", "SUBSYS1/PKT1")); // Not supported yet
        assertEquals(pkt1, mdb.getSequenceContainer("/REFMDB/SUBSYS1", "PKT1"));

        Parameter p = mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        assertNotNull(p);
        assertEquals(p, mdb.getParameter("/REFMDB/SUBSYS1", "IntegerPara1_1"));

        SpaceSystem ss = mdb.getSpaceSystem("/REFMDB/SUBSYS1");
        assertNotNull(ss);
        assertEquals(ss, mdb.getSpaceSystem("/REFMDB", "SUBSYS1"));

        FoundReference rr = refFinder.findReference(mdb.getRootSpaceSystem(),
                new NameReference("/REFMDB/SUBSYS1/IntegerPara1_1", Type.PARAMETER), ss);
        assertNotNull(rr);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1", rr.getNameDescription().getQualifiedName());

        rr = refFinder.findReference(mdb.getRootSpaceSystem(),
                new NameReference("../SUBSYS1/IntegerPara1_1", Type.PARAMETER), ss);
        assertNotNull(rr);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1", rr.getNameDescription().getQualifiedName());
    }
}
