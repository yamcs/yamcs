package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

public class BucketDbTest {

    static Path testDir = Path.of(System.getProperty("java.io.tmpdir"), "BucketDbTest");
    Random random = new Random();

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    public void cleanup() throws Exception {
        FileUtils.deleteRecursivelyIfExists(testDir);
    }

    @Test
    public void test1() throws Exception {
        String dir = testDir + File.separator + "tablespace1";
        Tablespace tablespace = new Tablespace("tablespace1");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);

        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());

        RdbBucket rdbBucket = bucketDb.createBucket("bucket1");
        assertNotNull(rdbBucket);
        Exception e = null;
        try {
            bucketDb.createBucket("bucket1");
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        List<Bucket> bl = bucketDb.listBuckets();
        assertEquals(1, bl.size());
        Bucket bucket = bl.get(0);
        assertEquals("bucket1", bucket.getName());
        assertEquals(0, bucket.getProperties().getSize());

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

        // closing and reopening
        tablespace.close();
        tablespace = new Tablespace("tablespace1bis");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        bucketDb = new RdbBucketDatabase("test", tablespace);

        bl = bucketDb.listBuckets();
        assertEquals(1, bl.size());
        bucket = bl.get(0);
        assertEquals("bucket1", bucket.getProperties().getName());
        assertEquals(1000, bucket.getProperties().getSize());
        assertEquals(1, bucket.getProperties().getNumObjects());

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
        String dir = testDir + File.separator + "tablespace2";
        Tablespace tablespace = new Tablespace("tablespace2");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);

        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());

        Bucket bucket = bucketDb.createBucket("bucket1");
        bucket.putObject("object1", null, new HashMap<>(), new byte[100]);
        bucket.putObject("object2", "plain/text", new HashMap<>(), new byte[100]);

        List<ObjectProperties> l = bucket.listObjects("object");
        assertEquals(2, l.size());

        assertEquals(4, tablespace.getRdb().getApproxNumRecords());
        bucketDb.deleteBucket("bucket1");

        tablespace.close();

        // closing and reopening
        tablespace = new Tablespace("tablespace2bis");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        bucketDb = new RdbBucketDatabase("test", tablespace);
        bucket = bucketDb.getBucket("bucket1");
        assertNull(bucket);
        tablespace.close();
    }

    @Test
    public void test3() throws Exception {
        RdbBucketDatabase bucketDb = createDb(3);
        Bucket b = bucketDb.createBucket("bucket1");
        Exception e = null;
        int n = RdbBucketDatabase.DEFAULT_MAX_OBJECTS_PER_BUCKET;
        try {
            for (int i = 0; i < n + 1; i++) {
                b.putObject("obj" + i, null, null, new byte[10]);
            }
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        b.deleteObject("obj0");
        b.putObject("newobj", null, null, new byte[10]);
        bucketDb.getTablespace().close();
    }

    @Test
    public void test4() throws Exception {
        RdbBucketDatabase bucketDb = createDb(4);
        Bucket b = bucketDb.createBucket("bucket1");
        Exception e = null;
        try {
            for (int i = 0; i < RdbBucketDatabase.DEFAULT_MAX_BUCKET_SIZE / (1024 * 1024) + 1; i++) {
                b.putObject("obj" + i, null, null, new byte[1024 * 1024]);
            }
        } catch (Exception e1) {
            e = e1;
        }

        assertNotNull(e);
        b.deleteObject("obj0");
        b.putObject("newobj", null, null, new byte[1024 * 1024]);
        bucketDb.getTablespace().close();
    }

    private RdbBucketDatabase createDb(int n) throws Exception {
        String dir = testDir + File.separator + "tablespace" + n;
        Tablespace tablespace = new Tablespace("tablespace" + n);
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        return new RdbBucketDatabase("test", tablespace);
    }
}
