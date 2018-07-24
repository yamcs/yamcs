package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.rocksdb.Tablespace;

public class ParameterIdMapTest {

    @Test
    public void test1() throws Exception {
        File f = new File("/tmp/TestParameterIdMap_test1");
        FileUtils.deleteRecursively(f.toPath());
        
        Tablespace tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.getAbsolutePath());
        
        tablespace.loadDb(false);
      
        
        ParameterIdDb pidMap = new ParameterIdDb("test1", tablespace);
        int p1 = pidMap.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        int p2 = pidMap.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        assertEquals(p1, p2);
        
        int p3 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE);
        assertTrue(p3 > p1);
        int p10 = pidMap.createAndGet("/test1/bla", Value.Type.DOUBLE, Value.Type.SINT32);
        assertTrue(p10 > p3);
        
        tablespace.close();
        tablespace.loadDb(false);
        
        pidMap = new ParameterIdDb("test1", tablespace);
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
