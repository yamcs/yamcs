package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.LongArray;
import org.yamcs.yarch.YarchException;

public class RdbSequenceTest {
    static Path dbdir;

    YRDB rdb;
    ColumnFamilyHandle cfMetadata;

    @BeforeClass
    public static void beforeClass() throws Exception {
        RocksDB.loadLibrary();
        dbdir = Files.createTempDirectory("rdbtest");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        FileUtils.deleteRecursively(dbdir);
    }

    @Before
    public void before() throws Exception {
        openDb();
    }

    @After
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
        List<Long> list = new ArrayList<>(n*m);
        for (int k = 0; k < n; k++) {
            LongArray la = f[k].get();
            assertEquals(m, la.size());
            for (int i = 0; i < m; i++) {
                list.add(la.get(i));
            }
        }
        Collections.sort(list);
        for (int i = 0; i < n * m; i++) {
            assertEquals(i, (long)list.get(i));
        }
    }

    
    @Test(expected=YarchException.class)
    public void testClose() throws Exception {
        RdbSequence seq = new RdbSequence("testclose", rdb, cfMetadata);
        seq.close();
        seq.next();
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
