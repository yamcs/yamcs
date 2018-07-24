package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.Tablespace;


public class ParameterGroupIdMapTest {
    @Test
    public void test1() throws Exception {
        File f = new File("/tmp/TestParameterGroupIdMap_test1");
        FileUtils.deleteRecursively(f.toPath());
        
        Tablespace tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.getAbsolutePath());
        tablespace.loadDb(false);
        
        ParameterGroupIdDb pgidMap = new ParameterGroupIdDb("test1", tablespace);
        int[] p1 = new int[] {1,3,4};
        int[] p2 = new int[] {1,3,4};
        int[] p3 = new int[] {1,4,5};
        
        int pg1 = pgidMap.createAndGet(p1);
        int pg3 = pgidMap.createAndGet(p3);
        int pg2 = pgidMap.createAndGet(p2);

        int[] ia = pgidMap.getAllGroups(1);
        assertArrayEquals(ia, new int[] {pg1, pg3});
        
        assertEquals(pg1, pg2);
        assertTrue(pg3 > pg1);
        
        tablespace.close();
        
        tablespace.loadDb(false);
        pgidMap = new ParameterGroupIdDb("test1", tablespace);
        
        int pg4 = pgidMap.createAndGet(p1);
        assertEquals(pg1, pg4);
        
        int[] p4 = new int[] {1,4,7};
        
        int pg6 = pgidMap.createAndGet(p4);
        
        assertTrue(pg6 > pg3);
        
        int[] ia1 = pgidMap.getAllGroups(1);
        assertArrayEquals(new int[] {pg1, pg3, pg6}, ia1);
    }

    void checkEquals(IntArray result, int...expected) {
        assertEquals(expected.length, result.size());
        for(int i=0;i<expected.length;i++) {
            assertEquals(expected[i], result.get(i));
        }
    }
}
