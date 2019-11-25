package org.yamcs.yarch;

import java.io.IOException;

public interface BackupControlMBean {

    public void createBackup(String tablespace, String backupDir) throws IOException;
}
