package org.yamcs.yarch;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSystemBucketTest {

    final String testDir = "/tmp/yamcs-filesystembuckettest";

    @Test
    public void testDirectoryTraversal() throws IOException {
        new File(testDir).mkdirs();
        FileSystemBucket bucket = new FileSystemBucket("bucket", Paths.get(testDir));

        IOException exception1 = assertThrows(IOException.class,
                () -> bucket.putObject("../traversed", "", new HashMap<>(), new byte[100]));
        assertTrue(exception1.getMessage().contains("Directory traversal"));

        IOException exception2 = assertThrows(IOException.class,
                () -> bucket.putObject("test/../../traversed", "", new HashMap<>(), new byte[100]));
        assertTrue(exception2.getMessage().contains("Directory traversal"));

        IOException exception3 = assertThrows(IOException.class,
                () -> bucket.putObject("/traversed", "", new HashMap<>(), new byte[100]));
        assertTrue(exception3.getMessage().contains("Directory traversal"));

        assertDoesNotThrow(() -> bucket.putObject("not-traversed", "", new HashMap<>(), new byte[100]));

        new File(testDir + "/test").mkdirs();
        assertDoesNotThrow(() -> bucket.putObject("test/../not-traversed", "", new HashMap<>(), new byte[100]));
    }
}
