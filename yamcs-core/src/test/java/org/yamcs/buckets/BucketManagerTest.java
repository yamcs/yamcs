package org.yamcs.buckets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.rocksdb.RdbBucket;

public class BucketManagerTest {

    Path dir;
    String prefix; // The local bucket database may be shared with other tests
    BucketManager bucketManager;

    @BeforeAll
    public static void beforeClass() {
        YConfiguration.setupTest(null);
    }

    @BeforeEach
    public void before() throws Exception {
        dir = Files.createTempDirectory("BucketManagerTest");
        prefix = "bmt" + UUID.randomUUID().toString().replace("-", "");
        bucketManager = new BucketManager();
    }

    @AfterEach
    public void after() throws Exception {
        for (var bucket : bucketManager.listBuckets()) {
            if (bucket instanceof RdbBucket && bucket.getName().startsWith(prefix)) {
                bucketManager.deleteBucket(bucket.getName());
            }
        }
        FileUtils.deleteRecursivelyIfExists(dir);
    }

    @Test
    public void testAddRemoveBucket() throws Exception {
        bucketManager.loadBuckets(null);

        var bucket = new FileSystemBucket(prefix + "a", dir);
        bucketManager.addBucket(bucket);
        assertSame(bucket, bucketManager.getBucket(prefix + "a"));
        assertTrue(bucketManager.listBuckets().contains(bucket));

        var duplicate = new FileSystemBucket(prefix + "a", dir);
        assertThrows(IllegalArgumentException.class, () -> bucketManager.addBucket(duplicate));

        assertSame(bucket, bucketManager.removeBucket(prefix + "a"));
        assertNull(bucketManager.getBucket(prefix + "a"));
        assertFalse(bucketManager.listBuckets().contains(bucket));

        assertNull(bucketManager.removeBucket(prefix + "unknown"));
    }

    @Test
    public void testRemoveLocalBucketRefused() throws Exception {
        bucketManager.loadBuckets(null);

        bucketManager.createBucket(prefix + "local");
        assertThrows(UnsupportedOperationException.class, () -> bucketManager.removeBucket(prefix + "local"));
        assertInstanceOf(RdbBucket.class, bucketManager.getBucket(prefix + "local"));
    }

    @Test
    public void testDeleteLocalBucket() throws Exception {
        bucketManager.loadBuckets(null);

        bucketManager.createBucket(prefix + "local");
        bucketManager.deleteBucket(prefix + "local");
        assertNull(bucketManager.getBucket(prefix + "local"));

        // Can be created again
        bucketManager.createBucket(prefix + "local");
    }

    @Test
    public void testCreateLocalByDefault() throws Exception {
        bucketManager.loadBuckets(serverConfig(providerConfig(false, true)));

        assertInstanceOf(FileSystemBucket.class, bucketManager.getBucket(prefix + "p1"));
        assertInstanceOf(RdbBucket.class, bucketManager.createBucket(prefix + "new"));
    }

    @Test
    public void testCreateThroughProvider() throws Exception {
        var config = serverConfig(providerConfig(true, true));
        config.getRoot().put("buckets", List.of(Map.of("name", prefix + "configured")));
        bucketManager.loadBuckets(config);

        assertInstanceOf(FileSystemBucket.class, bucketManager.getBucket(prefix + "p1"));

        // Bucket configured in yamcs.yaml is created through the provider
        assertInstanceOf(FileSystemBucket.class, bucketManager.getBucket(prefix + "configured"));
        assertTrue(Files.isDirectory(dir.resolve(prefix + "configured")));

        var bucket = bucketManager.createBucket(prefix + "new");
        assertInstanceOf(FileSystemBucket.class, bucket);
        assertSame(bucket, bucketManager.getBucket(prefix + "new"));
        assertTrue(Files.isDirectory(dir.resolve(prefix + "new")));

        assertThrows(IllegalArgumentException.class, () -> bucketManager.createBucket(prefix + "new"));
    }

    @Test
    public void testCreateThroughProviderUnsupported() throws Exception {
        bucketManager.loadBuckets(serverConfig(providerConfig(true, false)));

        assertThrows(UnsupportedOperationException.class, () -> bucketManager.createBucket(prefix + "new"));
        assertNull(bucketManager.getBucket(prefix + "new"));
    }

    @Test
    public void testDeleteThroughProvider() throws Exception {
        var providerConfig = providerConfig(true, true);
        providerConfig.put("canDelete", true);
        bucketManager.loadBuckets(serverConfig(providerConfig));

        // Bucket loaded by the provider
        bucketManager.deleteBucket(prefix + "p1");
        assertNull(bucketManager.getBucket(prefix + "p1"));
        assertFalse(Files.exists(dir.resolve(prefix + "p1")));

        // Bucket created through the provider
        bucketManager.createBucket(prefix + "new");
        bucketManager.deleteBucket(prefix + "new");
        assertNull(bucketManager.getBucket(prefix + "new"));
        assertFalse(Files.exists(dir.resolve(prefix + "new")));

        // Bucket added at runtime, with owner
        var provider = new TestBucketProvider();
        provider.loadBuckets(YConfiguration.wrap(providerConfig));
        bucketManager.addBucket(provider.createBucket(prefix + "added"), provider);
        bucketManager.deleteBucket(prefix + "added");
        assertNull(bucketManager.getBucket(prefix + "added"));
        assertFalse(Files.exists(dir.resolve(prefix + "added")));
    }

    @Test
    public void testDeleteNonEmptyLocalBucketRefused() throws Exception {
        bucketManager.loadBuckets(null);

        var bucket = bucketManager.createBucket(prefix + "local");
        bucket.putObjectAsync("a", null, Map.of(), new byte[] { 1 }).get();
        assertThrows(IllegalStateException.class, () -> bucketManager.deleteBucket(prefix + "local"));
        assertSame(bucket, bucketManager.getBucket(prefix + "local"));

        bucket.deleteObjectAsync("a").get();
        bucketManager.deleteBucket(prefix + "local");
        assertNull(bucketManager.getBucket(prefix + "local"));
    }

    @Test
    public void testDeleteNonEmptyProviderBucketRefused() throws Exception {
        var providerConfig = providerConfig(true, true);
        providerConfig.put("canDelete", true);
        bucketManager.loadBuckets(serverConfig(providerConfig));

        var bucket = bucketManager.getBucket(prefix + "p1");
        bucket.putObjectAsync("a", null, Map.of(), new byte[] { 1 }).get();
        assertThrows(IllegalStateException.class, () -> bucketManager.deleteBucket(prefix + "p1"));
        assertSame(bucket, bucketManager.getBucket(prefix + "p1"));
        assertTrue(Files.isDirectory(dir.resolve(prefix + "p1")));

        bucket.deleteObjectAsync("a").get();
        bucketManager.deleteBucket(prefix + "p1");
        assertNull(bucketManager.getBucket(prefix + "p1"));
    }

    @Test
    public void testDeleteThroughProviderUnsupported() throws Exception {
        bucketManager.loadBuckets(serverConfig(providerConfig(false, true)));

        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "p1"));
        assertInstanceOf(FileSystemBucket.class, bucketManager.getBucket(prefix + "p1"));
        assertTrue(Files.isDirectory(dir.resolve(prefix + "p1")));
    }

    @Test
    public void testDeleteWithoutOwnerUnsupported() throws Exception {
        bucketManager.loadBuckets(null);

        bucketManager.addBucket(new FileSystemBucket(prefix + "a", dir));
        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "a"));
        assertInstanceOf(FileSystemBucket.class, bucketManager.getBucket(prefix + "a"));

        bucketManager.createFileSystemBucket(prefix + "fs", dir.resolve("fs"));
        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "fs"));

        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "unknown"));
    }

    @Test
    public void testRemovedBucketNotDeletedThroughProvider() throws Exception {
        var providerConfig = providerConfig(false, true);
        providerConfig.put("canDelete", true);
        bucketManager.loadBuckets(serverConfig(providerConfig));

        // Removing forgets the owner: a bucket with the same name added later is not owned by the provider
        bucketManager.removeBucket(prefix + "p1");
        bucketManager.addBucket(new FileSystemBucket(prefix + "p1", dir.resolve(prefix + "p1")));
        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "p1"));
        assertTrue(Files.isDirectory(dir.resolve(prefix + "p1")));
    }

    @Test
    public void testProviderInstancePerEntry() throws Exception {
        var dir2 = dir.resolve("second");
        var providerConfig2 = providerConfig(false, true);
        providerConfig2.put("dir", dir2.toString());
        providerConfig2.put("buckets", List.of(prefix + "p2"));
        providerConfig2.put("canDelete", true);
        bucketManager.loadBuckets(serverConfig(providerConfig(true, true), providerConfig2));

        // Each entry has its own instance, keeping its own configuration
        assertTrue(Files.isDirectory(dir.resolve(prefix + "p1")));
        assertTrue(Files.isDirectory(dir2.resolve(prefix + "p2")));

        bucketManager.createBucket(prefix + "new");
        assertTrue(Files.isDirectory(dir.resolve(prefix + "new")));
        assertFalse(Files.exists(dir2.resolve(prefix + "new")));

        assertThrows(UnsupportedOperationException.class, () -> bucketManager.deleteBucket(prefix + "p1"));
        bucketManager.deleteBucket(prefix + "p2");
        assertFalse(Files.exists(dir2.resolve(prefix + "p2")));
    }

    @Test
    public void testOnlyOneCreationProvider() throws Exception {
        var config = serverConfig(providerConfig(true, true), providerConfig(true, true));
        assertThrows(ConfigurationException.class, () -> bucketManager.loadBuckets(config));
    }

    private Map<String, Object> providerConfig(boolean createBuckets, boolean canCreate) {
        var config = new HashMap<String, Object>();
        config.put("type", TestBucketProvider.LOCATION.name());
        config.put("dir", dir.toString());
        config.put("buckets", List.of(prefix + "p1"));
        config.put("createBuckets", createBuckets);
        config.put("canCreate", canCreate);
        return config;
    }

    @SafeVarargs
    private static YConfiguration serverConfig(Map<String, Object>... providerConfigs) {
        var config = new HashMap<String, Object>();
        config.put("bucketProviders", List.of(providerConfigs));
        return YConfiguration.wrap(config);
    }
}
