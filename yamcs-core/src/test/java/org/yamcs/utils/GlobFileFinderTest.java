package org.yamcs.utils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GlobFileFinderTest {
    static Path tempDir, p_a, p_aa, p_b, p_c, p_c_abc, p_c_bbb, p_c_cbb, p_d, p_d_abc;

    @BeforeClass
    public static void makeTempFiles() throws IOException {
        tempDir = Files.createTempDirectory("globtest");

        p_a = Files.createFile(tempDir.resolve("a"));
        p_aa = Files.createFile(tempDir.resolve("aa"));
        p_b = Files.createFile(tempDir.resolve("b"));
        p_c = Files.createDirectory(tempDir.resolve("c"));
        p_d = Files.createDirectory(tempDir.resolve("d"));
        p_c_abc = Files.createFile(p_c.resolve("abc"));
        p_c_bbb = Files.createFile(p_c.resolve("bbb"));
        p_c_cbb = Files.createFile(p_c.resolve("cbb"));
        p_d_abc = Files.createFile(p_d.resolve("abc"));

    }

    @AfterClass
    public static void cleanup() throws IOException {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testEmpty() throws Exception {
        GlobFileFinder gff = new GlobFileFinder();

        List<Path> l1 = gff.find(tempDir.resolve("*.txt").toString());
        assertEquals(0, l1.size());

        List<Path> l2 = gff.find(tempDir.resolve("..").toString());
        assertEquals(0, l2.size());

    }

    @Test
    public void testAbsolute() throws Exception {
        GlobFileFinder gff = new GlobFileFinder();

        List<Path> l1 = gff.find(tempDir.resolve("*").toString());
        assertEquals(3, l1.size());
        assertTrue(find(p_aa, l1));
        assertTrue(find(p_a, l1));
        assertTrue(find(p_b, l1));

        List<Path> l2 = gff.find(tempDir.resolve("a*").toString());
        assertEquals(2, l2.size());
        assertTrue(find(p_aa, l2));
        assertTrue(find(p_a, l2));
        assertFalse(find(p_b, l2));

        List<Path> l3 = gff.find(tempDir.resolve("c/*b*").toString());
        assertEquals(3, l3.size());
        assertTrue(find(p_c_abc, l3));
        assertTrue(find(p_c_bbb, l3));
        assertTrue(find(p_c_cbb, l3));

        List<Path> l4 = gff.find(tempDir.resolve("c/[a-b]*").toString());
        assertEquals(2, l4.size());
        assertTrue(find(p_c_abc, l4));
        assertTrue(find(p_c_bbb, l4));
    }

    @Test
    public void testRelative() throws Exception {
        GlobFileFinder gff = new GlobFileFinder();
        StringBuilder sb = new StringBuilder();
        Path cwd = path("").toAbsolutePath();
        for (int i = 0; i < cwd.getNameCount(); i++) {
            sb.append("../");
        }
        sb.append(tempDir.toString()).append("/*/a*");

        List<Path> l1 = gff.find(sb.toString());
        assertEquals(2, l1.size());
        assertTrue(find(p_c_abc, l1));
        assertTrue(find(p_d_abc, l1));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testIoLimit() throws Exception {
        GlobFileFinder gff = new GlobFileFinder();
        gff.setIoLimit(10);
        
        gff.find(tempDir.resolve("*/*").toString());
    }

    private boolean find(Path path, List<Path> plist) {
        return plist.stream()
                .map(p -> p.toAbsolutePath().normalize())
                .anyMatch(p -> p.compareTo(path) == 0);
    }

    static Path path(String name) {
        return FileSystems.getDefault().getPath(name);
    }
}
