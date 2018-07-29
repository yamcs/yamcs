package org.yamcs.server.cli;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.rocksdb.BackupEngine;
import org.rocksdb.BackupInfo;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.Options;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.yamcs.yarch.BackupUtils;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Command line backup utility for yamcs.
 * 
 * Taking a backup can be done while Yamcs server is running.
 * 
 * Restoring it has to be done while Yamcs is offline.
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Perform and restore backups")
public class Backup extends Command {
    public Backup(YamcsCtlCli yamcsCli) {
        super("backup", yamcsCli);
        addSubCommand(new BackupCreate());
        addSubCommand(new BackupDelete());
        addSubCommand(new BackupList());
        addSubCommand(new BackupRestore());
    }

    @Override
    void execute() throws Exception {
        RocksDB.loadLibrary();
        super.execute();
    }

    private abstract class BackupCommand extends Command {
        public BackupCommand(String name, Command parent) {
            super(name, parent);
        }

        @Parameter(names = "--backupDir", description = "backup directory")
        String backupDir;

    }

    @Parameters(commandDescription = "Create a new backup.")
    private class BackupCreate extends BackupCommand {
        @Parameter(names = "--dbDir", description = "database directory", required = true)
        String dbDir;

        public BackupCreate() {
            super("create", Backup.this);
        }

        @Override
        void execute() throws Exception {
            File current = new File(dbDir + File.separatorChar + "CURRENT");
            if (!current.exists()) {
                throw new Exception("'" + dbDir + "' does not look like a RocksDB database directory");
            }

            BackupUtils.verifyBackupDirectory(backupDir, false);
            try (Options opt = new Options();
                    BackupableDBOptions bopt = new BackupableDBOptions(backupDir);
                    ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
                    DBOptions dbOptions = new DBOptions();
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), bopt);) {

                List<byte[]> cfl = RocksDB.listColumnFamilies(opt, dbDir);
                List<ColumnFamilyDescriptor> cfdList = new ArrayList<>(cfl.size());

                for (byte[] b : cfl) {
                    cfdList.add(new ColumnFamilyDescriptor(b, cfOptions));
                }
                List<ColumnFamilyHandle> cfhList = new ArrayList<>(cfl.size());

                try (RocksDB db = RocksDB.open(dbOptions, dbDir, cfdList, cfhList)) {
                    backupEngine.createNewBackup(db);
                } finally {
                    for (final ColumnFamilyHandle cfh : cfhList) {
                        cfh.close();
                    }
                }

                console.println("Backup performed succesfully");
            } catch (RocksDBException e) {
                throw new IOException(
                        "Error when backing up database '" + dbDir + "' to '" + backupDir + "': " + e.getMessage());
            }
        }
    }

    @Parameters(commandDescription = "Restore a backup. This can only be done when Yamcs is not running.")
    private class BackupRestore extends BackupCommand {

        @Parameter(names = "--restoreDir", description = "restore directory (where the backup will be restored)", required = true)
        String restoreDir;

        @Parameter(names = "--backupId", description = "Backup id. If not specified, the last backup will be restored.")
        Integer backupId;

        public BackupRestore() {
            super("restore", Backup.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);
            try (BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                    RestoreOptions restoreOpt = new RestoreOptions(true);) {

                if (backupId != null) {
                    backupEngine.restoreDbFromBackup(backupId, restoreDir, restoreDir, restoreOpt);
                } else {
                    backupEngine.restoreDbFromLatestBackup(restoreDir, restoreDir, restoreOpt);
                }
            }
            console.println("Backup restored successfully to " + restoreDir);
        }
    }

    @Parameters(commandDescription = "List the existing backups")
    private class BackupList extends BackupCommand {
        public BackupList() {
            super("list", Backup.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);

            BackupableDBOptions opt = new BackupableDBOptions(backupDir);
            BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
            List<BackupInfo> blist = backupEngine.getBackupInfo();
            String sep = "+----------+---------------+----------+------------------------------+";
            final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
            console.println(sep);
            console.println(String.format("|%10s|%15s|%10s|%30s|", "backup id", "size (bytes)", "num files", "time"));
            console.println(sep);
            for (BackupInfo bi : blist) {
                console.println(String.format("|%10d|%15d|%10d|%30s|", bi.backupId(), bi.size(), bi.numberFiles(),
                        formatter.format(Instant.ofEpochMilli(1000 * bi.timestamp()))));
            }
            console.println(sep);
            backupEngine.close();
            opt.close();
        }

    }

    @Parameters(commandDescription = "Delete a backup")
    public class BackupDelete extends BackupCommand {
        @Parameter(names = "--backupId", description = "backup id", required = true)
        Integer backupId;

        public BackupDelete() {
            super("delete", Backup.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);
            try (BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);) {

                backupEngine.deleteBackup(backupId);
            }
            console.println("Backup with id " + backupId + " removed");
        }
    }
}
