package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GlobFileFinderTest {
    static Path tempDir, p_a, p_aa, p_b, p_c, p_c_abc, p_c_bbb, p_c_cbb, p_d, p_d_abc;

    @BeforeAll
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

    @AfterAll
    public static void cleanup() throws IOException {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testEmpty() {
        GlobFileFinder gff = new GlobFileFinder();

        List<Path> l1 = gff.find(tempDir + "/*.txt");
        assertEquals(0, l1.size());

        List<Path> l2 = gff.find(tempDir + "/..");
        assertEquals(0, l2.size());
    }

    @Test
    public void testAbsolute() {
        GlobFileFinder gff = new GlobFileFinder();

        List<Path> l1 = gff.find(tempDir + "/*");
        assertEquals(3, l1.size());
        assertTrue(find(p_aa, l1));
        assertTrue(find(p_a, l1));
        assertTrue(find(p_b, l1));

        List<Path> l2 = gff.find(tempDir + "/a*");
        assertEquals(2, l2.size());
        assertTrue(find(p_aa, l2));
        assertTrue(find(p_a, l2));
        assertFalse(find(p_b, l2));

        List<Path> l3 = gff.find(tempDir + "/c/*b*");
        assertEquals(3, l3.size());
        assertTrue(find(p_c_abc, l3));
        assertTrue(find(p_c_bbb, l3));
        assertTrue(find(p_c_cbb, l3));

        List<Path> l4 = gff.find(tempDir + "/c/[a-b]*");
        assertEquals(2, l4.size());
        assertTrue(find(p_c_abc, l4));
        assertTrue(find(p_c_bbb, l4));

        List<Path> l5 = gff.find(tempDir + "/c/?bb");
        assertEquals(2, l5.size());
        assertTrue(find(p_c_bbb, l5));
        assertTrue(find(p_c_cbb, l5));

        List<Path> l6 = gff.find(tempDir + "/c/{b,c}bb");
        assertEquals(2, l6.size());
        assertTrue(find(p_c_bbb, l6));
        assertTrue(find(p_c_cbb, l6));
    }

    @Test
    public void testRelative() {
        GlobFileFinder gff = new GlobFileFinder();

        List<Path> l1 = gff.find(tempDir, "../" + tempDir.getFileName() + "/*/a*");
        assertEquals(2, l1.size());
        assertTrue(find(p_c_abc, l1));
        assertTrue(find(p_d_abc, l1));
    }

    @Test
    public void testIoLimit() {
        assertThrows(IllegalArgumentException.class, () -> {
            GlobFileFinder gff = new GlobFileFinder();
            gff.setIoLimit(10);

            gff.find(tempDir.resolve("*/*").toString());
        });
    }

    private boolean find(Path path, List<Path> plist) {
        return plist.stream()
                .map(p -> p.toAbsolutePath().normalize())
                .anyMatch(p -> p.compareTo(path) == 0);
    }
}
