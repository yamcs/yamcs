package org.yamcs.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringEncoder;

/**
 * Command line backup utility for yamcs.
 * 
 * Taking a backup can be done via the REST interface when the Yamcs server is running.
 * 
 * Restoring it has to be done using this tool when the Yamcs server is not running. 
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Allows to perform and restore backups")
public class Backup extends Command {
    public Backup(YamcsCli yamcsCli) {
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
    private void error(String msg) {
        throw new ParameterException(getFullCommandName()+": "+msg);

    }

    public static void verifyBackupDirectory(String backupDir, boolean mustExist) throws IOException {
        Path path = FileSystems.getDefault().getPath(backupDir);
        if(Files.exists(path)) {
            if(!Files.isDirectory(path)) {
                throw new FileSystemException(backupDir, null, "File '"+backupDir+"' exists and is not a directory");
            }

            boolean isEmpty = true;
            boolean isBackupDir = false;
            try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
                for(Path p: dirStream) {
                    isEmpty = false;
                    if(p.endsWith("meta")) {
                        isBackupDir = true;
                        break;
                    }
                }
            }    

            if(!isEmpty && !isBackupDir) {
                throw new FileSystemException(backupDir, null, "Directory '"+backupDir+"' is not a backup directory");
            }
            if(!Files.isWritable(path)) {
                throw new FileSystemException(backupDir, null, "Directory '"+backupDir+"' is not writable");
            }
        } else {
            if(mustExist) {
                throw new FileSystemException(backupDir, null, "Directory '"+backupDir+"' does not exist");
            } else {
                Files.createDirectories(path);
            }
        }

    }

    private static RocksDB openDb(String dbDir) throws Exception {
        File current = new File(dbDir+File.separatorChar+"CURRENT");
        if(!current.exists()) {
            throw new Exception("'"+dbDir+"' does not look like a RocksDB database directory");
        }

        List<byte[]> cfl = RocksDB.listColumnFamilies(new Options(), dbDir);
        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
        DBOptions dbOptions = new DBOptions();


        List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());

        for(byte[] b: cfl) {
            cfdList.add(new ColumnFamilyDescriptor(b, cfOptions));                                      
        }
        List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
        return RocksDB.open(dbOptions, dbDir, cfdList, cfhList);
    }


    private abstract class BackupCommand extends Command {
        public BackupCommand(String name, Command parent) {
            super(name, parent);
        }
        @Parameter(names="--backupDir", description="backup directory")
        String backupDir;

    }
    @Parameters(commandDescription = "Create a new backup. Backups can be done directly or via a running Yamcs server.")
    private class BackupCreate extends BackupCommand {
        @Parameter(names="--dbDir", description="database directory", required=true)
        String dbDir;


        public BackupCreate() {
            super("create", Backup.this);
        }

        void validate() {
            YamcsConnectionProperties yamcsConn = getYamcsConnectionProperties();
            if(yamcsConn!=null) {
                if(yamcsConn.getInstance()==null) {
                    error("please specify the yamcs instance in the yamcs connection url (-y)");
                }
            }
        }


        @Override
        void execute() throws Exception {
            YamcsConnectionProperties yamcsConn = getYamcsConnectionProperties();
            if(yamcsConn==null) {
                //backup directly
                verifyBackupDirectory(backupDir, false);
                try {
                    BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);

                    RocksDB db = openDb(dbDir);
                    backupEngine.createNewBackup(db);

                    backupEngine.close();
                    db.close();
                    opt.close();
                } catch (Exception e) {
                    throw new Exception("Error when backing up database '"+dbDir+"' to '"+backupDir+"': "+e.getMessage());
                }
            } else {
                //make a rest request
                RestClient restClient = new RestClient(yamcsConn);
                QueryStringEncoder qse = new QueryStringEncoder("/archive/"+yamcsConn.getInstance()+"/rocksdb/backup"+dbDir);
                qse.addParam("backupDir", backupDir);
                String resource = qse.toString();
                try {
                    restClient.doRequest(resource, HttpMethod.POST).get();
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    throw new Exception("got error when performing POST request for resource '"+resource+"': "+t.getMessage());

                }
            }
            console.println("Backup performed succesfully");
        }

    }

    @Parameters(commandDescription = "Restore a backup. This can only be done when the Yamcs server is not running.")
    private class BackupRestore extends BackupCommand {

        @Parameter(names="--restoreDir", description="restore directory (where the backup will be restored)", required=true)
        String restoreDir;

        @Parameter(names = "--backupId", description="Backup id. If not specified, the last backup will be restored.")
        Integer backupId;

        public BackupRestore() {
            super("restore", Backup.this);
        }

        @Override
        void execute() throws Exception {
            verifyBackupDirectory(backupDir, true);
            BackupableDBOptions opt = new BackupableDBOptions(backupDir);
            BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
            RestoreOptions restoreOpt = new RestoreOptions(true);
            if(backupId!=null) {
                backupEngine.restoreDbFromBackup(backupId, restoreDir, restoreDir, restoreOpt);
            } else {
                backupEngine.restoreDbFromLatestBackup(restoreDir, restoreDir, restoreOpt);
            }
            backupEngine.close();
            restoreOpt.close();
            opt.close();
            console.println("Backup restored successfully to "+restoreDir);
        }
    }

    @Parameters(commandDescription = "List the existing backups")
    private class BackupList extends BackupCommand {
        public BackupList() {
            super("list", Backup.this);
        }
        @Override
        void execute() throws Exception {
            verifyBackupDirectory(backupDir, true);

            BackupableDBOptions opt = new BackupableDBOptions(backupDir);
            BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
            List<BackupInfo> blist= backupEngine.getBackupInfo();
            String sep = "+----------+---------------+----------+------------------------------+";
            final DateTimeFormatter formatter =  DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
            console.println(sep);
            console.println(String.format("|%10s|%15s|%10s|%30s|", "backup id", "size (bytes)", "num files", "time"));
            console.println(sep);
            for(BackupInfo bi: blist) {
                console.println(String.format("|%10d|%15d|%10d|%30s|", bi.backupId(), bi.size(), bi.numberFiles(), formatter.format(Instant.ofEpochMilli(1000*bi.timestamp()))));
            }
            console.println(sep);
            backupEngine.close();
            opt.close();
        }


    }

    @Parameters(commandDescription = "Delete a backup")
    public class BackupDelete extends BackupCommand {
        @Parameter(names = "--backupId", description="backup id", required=true)
        Integer backupId;

        public BackupDelete() {
            super("delete", Backup.this);
        }

        @Override
        void execute() throws Exception {
            verifyBackupDirectory(backupDir, true);
            BackupableDBOptions opt = new BackupableDBOptions(backupDir);
            BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
            backupEngine.deleteBackup(backupId);
            backupEngine.close();
            console.println("Backup with id "+backupId+" removed");
        }
    }
}