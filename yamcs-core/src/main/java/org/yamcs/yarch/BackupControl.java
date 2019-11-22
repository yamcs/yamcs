package org.yamcs.yarch;

import java.io.IOException;

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
        rdbFactory.doBackup(backupDir);
    }
}
