package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.FileUtils;

public class FileSystemBucketTest {

    private static final Path testDir = Path.of("/tmp/yamcs-filesystembuckettest");

    @BeforeEach
    public void beforeEach() throws IOException {
        FileUtils.deleteRecursivelyIfExists(testDir);
        Files.createDirectories(testDir);
    }

    @Test
    public void testDirectoryTraversal() throws IOException {
        var bucket = new FileSystemBucket("bucket", testDir.resolve("bucket"));
        Files.createDirectory(bucket.getBucketRoot());

        IOException exception1 = assertThrows(IOException.class,
                () -> bucket.putObject("../traversed", null, new HashMap<>(), new byte[100]));
        assertTrue(exception1.getMessage().contains("Directory traversal"));

        IOException exception2 = assertThrows(IOException.class,
                () -> bucket.putObject("test/../../traversed", null, new HashMap<>(), new byte[100]));
        assertTrue(exception2.getMessage().contains("Directory traversal"));

        IOException exception3 = assertThrows(IOException.class,
                () -> bucket.putObject("/traversed", null, new HashMap<>(), new byte[100]));
        assertTrue(exception3.getMessage().contains("Directory traversal"));

        assertDoesNotThrow(() -> bucket.putObject("not-traversed", null, new HashMap<>(), new byte[100]));

        Files.createDirectories(bucket.getBucketRoot().resolve("test"));
        assertDoesNotThrow(() -> bucket.putObject("test/../not-traversed", null, new HashMap<>(), new byte[100]));
    }

    @Test
    public void testSymlinkTraversal() throws IOException {
        var bucket = new FileSystemBucket("bucket", testDir.resolve("bucket"));
        Files.createDirectory(bucket.getBucketRoot());

        var target = testDir.resolve("symtarget"); // Sibling to bucket root
        Files.createDirectory(target);

        try {
            Files.createSymbolicLink(bucket.getBucketRoot().resolve("linked"), target);
            assertDoesNotThrow(() -> {
                bucket.putObject("linked/file.txt", null, Map.of(), new byte[100]);
            });
        } catch (UnsupportedOperationException e) {
            // Ignore, only test where symlinks are supported
        }
    }

    @AfterEach
    public void afterEach() throws IOException {
        FileUtils.deleteRecursivelyIfExists(testDir);
    }
}
