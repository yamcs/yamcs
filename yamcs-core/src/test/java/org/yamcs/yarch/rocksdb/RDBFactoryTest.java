package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;

public class RDBFactoryTest {

    @BeforeAll
    public static void initRocksDb() {
        RocksDB.loadLibrary();
    }

    private boolean isOpen(YRDB yrdb) {
        return yrdb.isOpen();
    }

    @Test
    public void testDispose() throws Exception {
        YRDB[] dbs = new YRDB[RDBFactory.maxOpenDbs * 2];
        RDBFactory rdbf = new RDBFactory("testDispose", new ScheduledThreadPoolExecutor(1));

        for (int i = 0; i < RDBFactory.maxOpenDbs; i++) {
            dbs[i] = rdbf.getRdb("/tmp/rdbfactorytest" + i, false);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs / 2; i++) {
            rdbf.dispose(dbs[i]);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs; i++) {
            assertTrue(isOpen(dbs[i]));
        }
        for (int i = RDBFactory.maxOpenDbs; i < 2 * RDBFactory.maxOpenDbs; i++) {
            dbs[i] = rdbf.getRdb("/tmp/rdbfactorytest" + i, false);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs / 2; i++) {
            assertFalse(isOpen(dbs[i]));
        }
        for (int i = RDBFactory.maxOpenDbs / 2; i < 2 * RDBFactory.maxOpenDbs; i++) {
            assertTrue(isOpen(dbs[i]));
        }
        // cleanup
        for (int i = 0; i < 2 * RDBFactory.maxOpenDbs; i++) {
            Path d = Paths.get("/tmp/rdbfactorytest" + i);
            FileUtils.deleteRecursivelyIfExists(d);
        }
    }

    @Test
    public void testBackup() throws Exception {
        String dir = "/tmp/rdb_backup_test/";
        FileUtils.deleteRecursivelyIfExists(Paths.get(dir));
        RDBFactory rdbf = new RDBFactory(dir.toString(), new ScheduledThreadPoolExecutor(1));

        YRDB db1 = rdbf.getRdb("db1", false);
        ColumnFamilyHandle cfh = db1.createColumnFamily("c1");
        db1.put(cfh, "aaa".getBytes(), "bbb".getBytes());

        db1.createColumnFamily("c2");

        new File(dir, "db1_back").mkdirs();
        rdbf.doBackup("db1", dir + "/db1_back").get();

        db1.createColumnFamily("c3");
        rdbf.doBackup("db1", dir + "/db1_back").get();

        // try to backup on top of existing non backup directory -> should throw an exception
        Throwable e = null;
        try {
            rdbf.doBackup("db1", dir + "/db1").get();
        } catch (ExecutionException e1) {
            e = e1.getCause();
        }
        assertNotNull(e);
        assertTrue(e instanceof FileSystemException);

        db1.put(cfh, "aaa1".getBytes(), "bbb1".getBytes());
        byte[] b = db1.get(cfh, "aaa1".getBytes());
        assertNotNull(b);
        rdbf.close(db1);

        rdbf.restoreBackup(1, dir + "/db1_back", "db2").get();
        YRDB db2 = rdbf.getRdb("db2", false);

        assertNotNull(db2.getColumnFamilyHandle("c2"));
        assertNull(db2.getColumnFamilyHandle("c3"));

        ColumnFamilyHandle cfh_db2 = db2.getColumnFamilyHandle("c1");
        assertNotNull(cfh_db2);

        b = db2.get(cfh_db2, "aaa".getBytes());
        assertNotNull(b);
        b = db2.get(cfh_db2, "aaa1".getBytes());
        assertNull(b);

        rdbf.restoreBackup(-1, dir + "/db1_back", "db3").get();
        YRDB db3 = rdbf.getRdb("db3", false);

        assertNotNull(db3.getColumnFamilyHandle("c2"));
        assertNotNull(db3.getColumnFamilyHandle("c3"));
    }
}
