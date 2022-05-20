package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.LongArray;
import org.yamcs.yarch.YarchException;

public class RdbSequenceTest {
    static Path dbdir;

    YRDB rdb;
    ColumnFamilyHandle cfMetadata;

    @BeforeAll
    public static void beforeClass() throws Exception {
        RocksDB.loadLibrary();
        dbdir = Files.createTempDirectory("rdbtest");
    }

    @AfterAll
    public static void afterClass() throws Exception {
        FileUtils.deleteRecursively(dbdir);
    }

    @BeforeEach
    public void before() throws Exception {
        openDb();
    }

    @AfterEach
    public void after() throws Exception {
        closeDb();
    }

    @Test
    public void test1() throws Exception {
        RdbSequence seq = new RdbSequence("test1", rdb, cfMetadata);
        for (long i = 0; i < 234; i++) {
            assertEquals(i, seq.next());
        }
        seq.close();
        closeDb();
        openDb();

        RdbSequence seq1 = new RdbSequence("test1", rdb, cfMetadata);
        for (long i = 234; i < 543; i++) {
            assertEquals(i, seq1.next());
        }
        seq1.reset(100);
        assertEquals(100, seq1.next());
        seq1.close();
        closeDb();
        openDb();

        RdbSequence seq2 = new RdbSequence("test1", rdb, cfMetadata);
        assertEquals(101, seq2.next());

    }

    @Test
    public void testnthreads() throws Exception {
        int n = 200;
        int m = 1000;
        RdbSequence seq = new RdbSequence("testnthreads", rdb, cfMetadata);

        ExecutorService executor = Executors.newFixedThreadPool(n);
        Future<LongArray>[] f = new Future[n];

        for (int k = 0; k < n; k++) {
            f[k] = executor.submit(() -> {
                LongArray a = new LongArray(m);
                for (int i = 0; i < m; i++) {
                    a.add(seq.next());
                }
                return a;
            });
        }
        List<Long> list = new ArrayList<>(n * m);
        for (int k = 0; k < n; k++) {
            LongArray la = f[k].get();
            assertEquals(m, la.size());
            for (int i = 0; i < m; i++) {
                list.add(la.get(i));
            }
        }
        Collections.sort(list);
        for (int i = 0; i < n * m; i++) {
            assertEquals(i, (long) list.get(i));
        }
    }

    @Test
    public void testClose() {
        assertThrows(YarchException.class, () -> {
            RdbSequence seq = new RdbSequence("testclose", rdb, cfMetadata);
            seq.close();
            seq.next();
        });
    }

    private void openDb() throws Exception {
        rdb = new YRDB(dbdir.toString(), false);
        cfMetadata = rdb.getColumnFamilyHandle("_metadata_");
        if (cfMetadata == null) {
            cfMetadata = rdb.createColumnFamily("_metadata_");
        }
    }

    private void closeDb() {
        cfMetadata.close();
        rdb.close();
    }
}
