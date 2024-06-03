package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.rocksdb.RdbBucket;
import org.yamcs.yarch.rocksdb.RdbBucketDatabase;
import org.yamcs.yarch.rocksdb.Tablespace;

public class BackupCliTest extends AbstractCliTest {
    static Path etcdata;

    @BeforeAll
    public static void createDb() throws IOException {
        RocksDB.loadLibrary();
        etcdata = createTmpEtcData();
    }

    @AfterAll
    public static void cleanup() throws IOException {
        FileUtils.deleteRecursively(etcdata);
    }

    @Test
    public void test1() throws Exception {
        TimeEncoding.setUp();

        // create some data
        Random r = new Random();
        byte[] obj1 = new byte[1024];
        byte[] obj2 = new byte[1024];
        r.nextBytes(obj1);
        r.nextBytes(obj2);
        String dataDir = etcdata.toString();
        String backupDir = etcdata.resolve("backup").toString();
        String restore1Dir = etcdata.resolve("restore1").toString();
        String restore2Dir = etcdata.resolve("restore2").toString();

        Tablespace tbl = new Tablespace("test");
        tbl.setCustomDataDir(dataDir + File.separator + "test.rdb");
        tbl.loadDb(false);
        BucketDatabase bdb = new RdbBucketDatabase("test", tbl);
        Bucket bucket = bdb.createBucket("mybucket");
        bucket.putObject("obj1", "binary", null, obj1);
        tbl.close();

        // create backup 1 containing obj1
        runMain("--debug", "backup", "create", "--backup-dir", backupDir, "--data-dir", dataDir, "test");
        assertTrue(mconsole.output().contains("Backup performed successfully"));
        verifyBackupList(backupDir, 1);

        tbl.loadDb(false);

        bucket.putObject("obj2", "binary", null, obj2);

        tbl.close();

        // create backup 2 containing obj1 and obj2
        mconsole.reset();
        runMain("--debug", "backup", "create", "--backup-dir", backupDir, "--data-dir", dataDir, "test");
        assertTrue(mconsole.output().contains("Backup performed successfully"));

        verifyBackupList(backupDir, 1, 2);

        // restore backup1
        RdbBucket rbucket1 = restoreBackup(backupDir, restore1Dir, 1);
        byte[] robj1 = rbucket1.getObject("obj1");
        assertArrayEquals(obj1, robj1);
        assertNull(rbucket1.getObject("obj2"));
        rbucket1.getTablespace().close();

        // restore backup 2
        RdbBucket rbucket2 = restoreBackup(backupDir, restore2Dir, 2);
        robj1 = rbucket2.getObject("obj1");
        assertArrayEquals(obj1, robj1);

        byte[] robj2 = rbucket2.getObject("obj2");
        assertArrayEquals(obj2, robj2);
        rbucket2.getTablespace().close();

        // create backup 3
        mconsole.reset();
        runMain("--debug", "backup", "create", "--backup-dir", backupDir, "--data-dir", dataDir, "test");
        assertTrue(mconsole.output().contains("Backup performed successfully"));

        verifyBackupList(backupDir, 1, 2, 3);

        // delete backup 2
        mconsole.reset();
        runMain("--debug", "backup", "delete", "--backup-dir", backupDir, "2");

        assertTrue(mconsole.output().contains("Deleted backup 2"));

        verifyBackupList(backupDir, 1, 3);

        // purge backup
        mconsole.reset();
        runMain("--debug", "backup", "purge", "--backup-dir", backupDir, "--keep", "1");

        assertTrue(mconsole.output().contains("Purged operation successful"));
        verifyBackupList(backupDir, 3);
    }

    void verifyBackupList(String backupDir, int... id) {
        mconsole.reset();
        runMain("--debug", "backup", "list", "--backup-dir", backupDir);
        String[] lines = mconsole.output().split("\n");
        assertEquals(id.length + 1, lines.length);
        for (int i = 0; i < id.length; i++) {
            assertTrue(lines[i + 1].startsWith(Integer.toString(id[i])));
        }
    }

    RdbBucket restoreBackup(String backupDir, String restoreDir, int id) throws RocksDBException, IOException {
        mconsole.reset();
        runMain("backup", "restore", "--backup-dir", backupDir, "--restore-dir", restoreDir,
                Integer.toString(id));
        Tablespace rtbl = new Tablespace("restore" + id);
        rtbl.setCustomDataDir(restoreDir);
        rtbl.loadDb(false);

        RdbBucketDatabase bdb = new RdbBucketDatabase("restore" + id, rtbl);
        RdbBucket rbucket = bdb.getBucket("mybucket");
        assertNotNull(rbucket);
        return rbucket;
    }
}
