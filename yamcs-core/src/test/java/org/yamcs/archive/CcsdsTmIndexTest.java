package org.yamcs.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.archive.CcsdsTmIndex.CcsdsIndexIterator;
import org.yamcs.yarch.YarchTestCase;

public class CcsdsTmIndexTest extends YarchTestCase {

    static Map<String, Object> config = new HashMap<>();
    static {
        config.put("streams", new ArrayList<String>());
    }

    @BeforeAll
    public static void oneTimeSetup() throws Exception {
        YConfiguration.setupTest(null);
    }

    private static void assertEqual(Record r, long start, long stop, int nump) {
        assertEquals(start, r.firstTime());
        assertEquals(stop, r.lastTime());
        assertEquals(nump, r.numPackets());
    }

    @Test
    public void testCompare() {
        assertTrue(CcsdsTmIndex.compare(0L, (short) 1, 1L, (short) 5) < -1);
        assertTrue(CcsdsTmIndex.compare(1L, (short) 5, 0L, (short) 1) > 1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 1, 2L, (short) 2) == -1);
        assertTrue(CcsdsTmIndex.compare(2L, (short) 2, 0L, (short) 1) == 1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 1, 0L, (short) 5) < -1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 5, 0L, (short) 1) > 1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 1, 0L, (short) 2) == -1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 2, 0L, (short) 1) == 1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 0x3FFF, 0L, (short) 0) == -1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 0, 0L, (short) 0x3FFF) == 1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 0x3FEF, 0L, (short) 5) < -1);
        assertTrue(CcsdsTmIndex.compare(0L, (short) 5, 0L, (short) 0x3FEF) > 1);
        assertTrue(CcsdsTmIndex.compare(2L, (short) 2, 2L, (short) 2) == 0);
    }

    @Test
    public void testApidIndex() throws Exception {
        CcsdsTmIndex tmindex = new CcsdsTmIndex();
        tmindex.init(ydb.getName(), "test", YConfiguration.wrap(config));

        CcsdsIndexIterator it1 = tmindex.new CcsdsIndexIterator((short) -1, -1L, -1L);
        assertNull(it1.getNextRecord());

        short apid = 1000;
        short apid1 = 1001;

        tmindex.addPacket(apid, 10L, (short) 5);
        CcsdsIndexIterator it = tmindex.new CcsdsIndexIterator((short) -1, -1L, -1L);
        assertEqual(it.getNextRecord(), 10, 10, 1);
        it.close();

        tmindex.addPacket(apid, 8L, (short) 4);
        it = tmindex.new CcsdsIndexIterator((short) -1, -1L, -1L);
        assertEqual(it.getNextRecord(), 8, 10, 2);
        it.close();

        tmindex.addPacket(apid1, 10L, (short) 5);

        tmindex.addPacket(apid, 1L, (short) 1);
        tmindex.addPacket(apid, 10L, (short) 51);
        tmindex.addPacket(apid, 10L, (short) 52);
        tmindex.addPacket(apid, 10L, (short) 102);
        tmindex.addPacket(apid, 4001L, (short) 1);
        tmindex.addPacket(apid, 4000L, (short) 0x3FFF);
        tmindex.addPacket(apid, 4001L, (short) 0);
        tmindex.addPacket(apid, 8L, (short) 4);

        it = tmindex.new CcsdsIndexIterator((short) -1, -1L, -1L);
        assertEqual(it.getNextRecord(), 1, 1, 1);
        assertEqual(it.getNextRecord(), 8, 10, 2);

        assertEqual(it.getNextRecord(), 10, 10, 2);
        assertEqual(it.getNextRecord(), 10, 10, 1);
        assertEqual(it.getNextRecord(), 4000, 4001, 3);
        Record r = it.getNextRecord();
        assertEquals(apid1, r.apid());
        assertEqual(r, 10, 10, 1);

        assertNull(it.getNextRecord());

        // tmindex.printApidDb();
    }

    @Test
    @Disabled
    public void testApidIndexSameTimeAndWraparound() throws Exception {
        CcsdsTmIndex tmindex = new CcsdsTmIndex();
        tmindex.init(ydb.getName(), "test", YConfiguration.emptyConfig());

        short apid = 2000;

        tmindex.addPacket(apid, 5000L, (short) 1);
        tmindex.addPacket(apid, 5000L, (short) 0x3FFF);
        tmindex.addPacket(apid, 5000L, (short) 0);

        CcsdsIndexIterator it = tmindex.new CcsdsIndexIterator((short) -1, -1L, -1L);

        assertEqual(it.getNextRecord(), 5000, 5000, 3);
        assertNull(it.getNextRecord());
    }
}
