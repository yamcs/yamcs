package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.Tablespace;

public class ParameterGroupIdMapTest {

    @Test
    public void testFullOverlap() throws Exception {
        Path f = Path.of(System.getProperty("java.io.tmpdir"), "TestParameterGroupIdMap_testFullOverlap");
        FileUtils.deleteRecursivelyIfExists(f);

        Tablespace tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.toString());
        tablespace.loadDb(false);

        ParameterGroupIdDb pgidMap = new ParameterGroupIdDb("test1", tablespace, false, 0);
        IntArray p1 = IntArray.wrap(1, 3, 4);
        IntArray p2 = IntArray.wrap(1, 3, 4);
        IntArray p3 = IntArray.wrap(1, 4, 5);

        var pg1 = pgidMap.getGroup(p1);
        var pg3 = pgidMap.getGroup(p3);
        var pg2 = pgidMap.getGroup(p2);

        int[] ia = pgidMap.getAllGroups(1);
        assertArrayEquals(new int[] { pg1.id, pg3.id }, ia);

        assertEquals(pg1, pg2);
        assertTrue(pg3.id > pg1.id);

        tablespace.close();

        tablespace.loadDb(false);
        pgidMap = new ParameterGroupIdDb("test1", tablespace, false, 0);

        var pg4 = pgidMap.getGroup(p1);
        assertEquals(pg1.id, pg4.id);

        IntArray p4 = IntArray.wrap(1, 4, 7);

        var pg6 = pgidMap.getGroup(p4);

        assertTrue(pg6.id > pg3.id);

        int[] ia1 = pgidMap.getAllGroups(1);
        assertArrayEquals(new int[] { pg1.id, pg3.id, pg6.id }, ia1);
    }

    @Test
    public void testHalfOverlap() throws Exception {
        Path f = Path.of(System.getProperty("java.io.tmpdir"), "TestParameterGroupIdMap_testHalfOverlap");
        FileUtils.deleteRecursivelyIfExists(f);

        Tablespace tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.toString());
        tablespace.loadDb(false);

        ParameterGroupIdDb pgidMap = new ParameterGroupIdDb("test1", tablespace, true, 0.5);
        IntArray p1 = IntArray.wrap(1, 3, 4);
        IntArray p2 = IntArray.wrap(1, 3, 5);
        IntArray p3 = IntArray.wrap(1, 6, 7);

        var pg1 = pgidMap.getGroup(p1);
        var pg2 = pgidMap.getGroup(p2);
        var pg3 = pgidMap.getGroup(p3);

        assertEquals(pg1.id, pg2.id);
        assertEquals(4, pg1.pids.size());

        assertTrue(pg3.id > pg2.id);
        assertEquals(3, pg3.pids.size());

    }

    void checkEquals(IntArray result, int... expected) {
        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result.get(i));
        }
    }
}
