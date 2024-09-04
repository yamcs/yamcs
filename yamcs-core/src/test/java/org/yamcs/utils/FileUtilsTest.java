package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class FileUtilsTest {

    @Test
    public void testGetFileExtension() {
        var p = Path.of("a/b/cc.txt");
        assertEquals("txt", FileUtils.getFileExtension(p));

        p = Path.of("a/b/cc.TXT");
        assertEquals("txt", FileUtils.getFileExtension(p));

        p = Path.of("a/b/cc");
        assertNull(FileUtils.getFileExtension(p));

        p = Path.of("a/b/cc.");
        assertNull(FileUtils.getFileExtension(p));

        p = Path.of(".gitignore");
        assertEquals("gitignore", FileUtils.getFileExtension(p));
    }
}
