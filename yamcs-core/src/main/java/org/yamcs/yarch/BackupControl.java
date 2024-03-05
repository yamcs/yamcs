package org.yamcs.yarch;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.yamcs.logging.Log;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;

/**
 * JMX MBean for performing a hot backup of a tablespace.
 */
public class BackupControl implements BackupControlMBean {

    private static final Log log = new Log(BackupControl.class);

    @Override
    public void createBackup(String tablespaceName, String backupDir) throws IOException {
        log.info("Backing up tablespace {} to {}", tablespaceName, backupDir);
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        Tablespace tablespace = rse.getTablespace(tablespaceName);
        if (tablespace == null) {
            throw new IllegalArgumentException("No tablespace by name '" + tablespaceName + "'");
        }

        BackupUtils.verifyBackupDirectory(backupDir, false);
        RDBFactory rdbFactory = tablespace.getRdbFactory();

        try {
            rdbFactory.doBackup(backupDir).get();
            log.info("Backup finished");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException e) {
            log.error("Error while creating backup", e);
        }
    }
}
