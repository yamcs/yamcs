package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.FileUtils;

public class TestParameterIdMap {

    @Test
    public void test1() throws Exception {
        File f = new File("/tmp/TestParameterIdMap_test1");
        FileUtils.deleteRecursively(f.toPath());
        RocksDB db = RocksDB.open(f.getAbsolutePath());
        ColumnFamilyHandle cfh =  db.getDefaultColumnFamily();
        
        ParameterIdDb pidMap = new ParameterIdDb(db, cfh);
        int p1 = pidMap.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        int p2 = pidMap.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        assertEquals(p1, p2);
        
        int p3 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE);
        assertTrue(p3 > p1);
        int p10 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE, Value.Type.SINT32);
        assertTrue(p10 > p3);
        
        
        db.close();
        
        db = RocksDB.open(f.getAbsolutePath());
        cfh =  db.getDefaultColumnFamily();
        pidMap = new ParameterIdDb(db, cfh);
        int p4 = pidMap.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        assertEquals(p1, p4);
        int p5 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE);
        assertEquals(p3, p5);
        
        int p6 = pidMap.createAndGet("/test2/bla", Value.Type.DOUBLE);
        assertTrue(p6 > p3);
        
        int p11 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE, Value.Type.SINT32);
        assertEquals(p10, p11);
        
    }

}
