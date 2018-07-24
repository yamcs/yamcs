package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

public class BucketDbTest {
    static String testDir = "/tmp/BucketDbTest";
    Random random = new Random();
    @BeforeClass
    static public void beforeClass() {
        TimeEncoding.setUp();
    }
    @Before
    public void cleanup() throws Exception {
        FileUtils.deleteRecursively(testDir);
    }
    
    @Test
    public void test1() throws Exception {
        String dir = testDir+"/tablespace1";
        Tablespace tablespace = new Tablespace("tablespace1");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        
        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());
        
        RdbBucket bucket = bucketDb.createBucket("bucket1");
        assertNotNull(bucket);
        Exception e = null;
        try {
            bucketDb.createBucket("bucket1");
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        List<BucketProperties> bpl = bucketDb.listBuckets();
        assertEquals(1, bpl.size());
        BucketProperties bp = bpl.get(0);
        assertEquals("bucket1", bp.getName());
        assertEquals(0, bp.getSize());
        
        assertTrue(bucket.listObjects(x -> true).isEmpty());
        Map<String, String> props = new HashMap<>();
        props.put("prop1", "value1");
        props.put("prop2", "value2");
        byte[] objectData = new byte[1000];
        random.nextBytes(objectData);
        bucket.putObject("object1", null, props, objectData);
        List<ObjectProperties> l = bucket.listObjects(x -> true);
        assertEquals(1, l.size());
        assertEquals("object1", l.get(0).getName());
        
        byte[] b = bucket.getObject("object1");
        assertArrayEquals(objectData, b);
        
        //closing and reopening
        tablespace.close();
        tablespace = new Tablespace("tablespace1bis");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        bucketDb = new RdbBucketDatabase("test", tablespace);
         
        bpl = bucketDb.listBuckets();
        assertEquals(1, bpl.size());
        bp = bpl.get(0);
        assertEquals("bucket1", bp.getName());
        assertEquals(1000, bp.getSize());
        assertEquals(1, bp.getNumObjects());
        
        bucket = bucketDb.getBucket("bucket1");
        
        l = bucket.listObjects(x -> true);
        assertEquals(1, l.size());
        assertEquals("object1", l.get(0).getName());
        
      
        
        l = bucket.listObjects("x", x -> true);
        assertEquals(0, l.size());
        
        b = bucket.getObject("object1");
        assertArrayEquals(objectData, b);
        
        
        bucket.deleteObject("object1");
        assertTrue(bucket.listObjects(x -> true).isEmpty());
        
        bucketDb.deleteBucket("bucket1");
        assertTrue(bucketDb.listBuckets().isEmpty());
        tablespace.close();
    }    

    @Test
    public void test2() throws Exception {
        String dir = testDir+"/tablespace2";
        Tablespace tablespace = new Tablespace("tablespace2");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        
        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());
        
        Bucket bucket = bucketDb.createBucket("bucket1");
        bucket.putObject("object1", null, new HashMap<>(), new byte[100]);
        bucket.putObject("object2", "plain/text", new HashMap<>(), new byte[100]);
        
        List<ObjectProperties> l  = bucket.listObjects("object");
        assertEquals(2, l.size());
        
        assertEquals(4, tablespace.getRdb().getApproxNumRecords());
        bucketDb.deleteBucket("bucket1");
        
        tablespace.close();
        
        //closing and reopening
        tablespace = new Tablespace("tablespace2bis");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        bucketDb = new RdbBucketDatabase("test", tablespace);
        bucket = bucketDb.getBucket("bucket1");
        assertNull(bucket);
    }
    
    @Test
    public void test3() throws Exception {
        RdbBucketDatabase bucketDb = createDb(3); 
        Bucket b = bucketDb.createBucket("bucket1");
        Exception e = null;
        int n = RdbBucketDatabase.MAX_NUM_OBJECTS_PER_BUCKET;
        try {
            for(int i=0; i<n+1; i++) {
                b.putObject("obj"+i, null, null, new byte[10]);
            }
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        b.deleteObject("obj0");
        b.putObject("newobj", null, null, new byte[10]);
    }
    
    
    @Test
    public void test4() throws Exception {
        RdbBucketDatabase bucketDb = createDb(4); 
        Bucket b = bucketDb.createBucket("bucket1");
        Exception e = null;
        try {
            for(int i=0; i<RdbBucketDatabase.MAX_BUCKET_SIZE/(1024*1024)+1; i++) {
                b.putObject("obj"+i, null, null, new byte[1024*1024]);
            }
        } catch (Exception e1) {
            e = e1;
        }
        
        assertNotNull(e);
        b.deleteObject("obj0");
        b.putObject("newobj", null, null, new byte[1024*1024]);
    }
    
    private RdbBucketDatabase createDb(int n) throws Exception {
        String dir = testDir+"/tablespace"+n;
        Tablespace tablespace = new Tablespace("tablespace"+n);
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        return new RdbBucketDatabase("test", tablespace);
    }
    
    
}
