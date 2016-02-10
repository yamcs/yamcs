package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;


public class TestParameterGroupIdMap {

    @Test
    public void test1() throws Exception {
        File f = new File("/tmp/TestParameterIdMap_test1");
        FileUtils.deleteRecursively(f.toPath());
        RocksDB db = RocksDB.open(f.getAbsolutePath());
        ColumnFamilyHandle cfh =  db.getDefaultColumnFamily();
        
        ParameterGroupIdDb pgidMap = new ParameterGroupIdDb(db, cfh);
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
        
        db.close();
        
        db = RocksDB.open(f.getAbsolutePath());
        cfh =  db.getDefaultColumnFamily();
        pgidMap = new ParameterGroupIdDb(db, cfh);
        
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
