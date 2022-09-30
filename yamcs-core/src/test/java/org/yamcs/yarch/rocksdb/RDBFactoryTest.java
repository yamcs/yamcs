package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "testDispose");
        RDBFactory rdbf = new RDBFactory(dataDir.toString(), new ScheduledThreadPoolExecutor(1));

        for (int i = 0; i < RDBFactory.maxOpenDbs; i++) {
            dbs[i] = rdbf.getRdb("rdbfactorytest" + i, false);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs / 2; i++) {
            rdbf.dispose(dbs[i]);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs; i++) {
            assertTrue(isOpen(dbs[i]));
        }
        for (int i = RDBFactory.maxOpenDbs; i < 2 * RDBFactory.maxOpenDbs; i++) {
            dbs[i] = rdbf.getRdb("rdbfactorytest" + i, false);
        }
        for (int i = 0; i < RDBFactory.maxOpenDbs / 2; i++) {
            assertFalse(isOpen(dbs[i]));
        }
        for (int i = RDBFactory.maxOpenDbs / 2; i < 2 * RDBFactory.maxOpenDbs; i++) {
            assertTrue(isOpen(dbs[i]));
        }
        // cleanup

        for (int i = 0; i < 2 * RDBFactory.maxOpenDbs; i++) {
            rdbf.closeIfOpen("rdbfactorytest" + i);
            Path d = dataDir.resolve("rdbfactorytest" + i);
            FileUtils.deleteRecursivelyIfExists(d);
        }
    }

    @Test
    public void testBackup() throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "rdb_backup_test");
        FileUtils.deleteRecursivelyIfExists(dir);
        RDBFactory rdbf = new RDBFactory(dir.toString(), new ScheduledThreadPoolExecutor(1));

        YRDB db1 = rdbf.getRdb("db1", false);
        ColumnFamilyHandle cfh = db1.createColumnFamily("c1");
        db1.put(cfh, "aaa".getBytes(), "bbb".getBytes());

        db1.createColumnFamily("c2");

        Path backupDir = dir.resolve("db1_back");
        Files.createDirectories(backupDir);
        rdbf.doBackup("db1", backupDir.toString()).get();

        db1.createColumnFamily("c3");
        rdbf.doBackup("db1", backupDir.toString()).get();

        // try to backup on top of existing non backup directory -> should throw an exception
        assertThrows(FileSystemException.class, () -> {
            try {
                rdbf.doBackup("db1", dir.resolve("db1").toString()).get();
            } catch (ExecutionException e1) {
                throw e1.getCause();
            }
        });

        db1.put(cfh, "aaa1".getBytes(), "bbb1".getBytes());
        byte[] b = db1.get(cfh, "aaa1".getBytes());
        assertNotNull(b);
        rdbf.close(db1);

        rdbf.restoreBackup(1, backupDir.toString(), "db2").get();
        YRDB db2 = rdbf.getRdb("db2", false);

        assertNotNull(db2.getColumnFamilyHandle("c2"));
        assertNull(db2.getColumnFamilyHandle("c3"));

        ColumnFamilyHandle cfh_db2 = db2.getColumnFamilyHandle("c1");
        assertNotNull(cfh_db2);

        b = db2.get(cfh_db2, "aaa".getBytes());
        assertNotNull(b);
        b = db2.get(cfh_db2, "aaa1".getBytes());
        assertNull(b);

        rdbf.restoreBackup(-1, backupDir.toString(), "db3").get();
        YRDB db3 = rdbf.getRdb("db3", false);

        assertNotNull(db3.getColumnFamilyHandle("c2"));
        assertNotNull(db3.getColumnFamilyHandle("c3"));
    }
}
