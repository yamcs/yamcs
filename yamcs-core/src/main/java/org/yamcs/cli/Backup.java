package org.yamcs.cli;

import java.io.File;
import java.nio.file.DirectoryStream;
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
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.RestClient;
import org.yamcs.cli.YamcsCli.Command;

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
@Parameters(commandDescription = "Backup operations")
public class Backup extends Command {

    @Parameter(names="-b", description="backup database")
    private boolean backupOp;

    @Parameter(names="-r", description="restore database")
    private boolean restoreOp;

    @Parameter(names="-l", description="list backups")
    private boolean listOp;

    @Parameter(names="-d", description="delete backup")
    private boolean deleteOp;


    @Parameter(names="--backupDir", description="backup directory")
    String backupDir;

    @Parameter(names="--restoreDir", description="restore directory (where the backup will be restored)")
    String restoreDir;

    @Parameter(names="--dbDir", description="database directory")
    String dbDir;

    @Parameter(names = "--backupId", description="backup id (used for restore and delete)")
    Integer backupId;

    public String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append( "Usage: \n" );
        sb.append( "\t backup <operation> [parameters] \n" );
        sb.append( "\t operation: \n");
        sb.append( "\t\t -b perform backup. If the yamcs url is specified (--url) then a request to a running yamcs will be made.\n");
        sb.append( "\t\t    Otherwise, the local directory will be backed up (but this will fail if \n");
        sb.append( "\t\t -r restore backup. The backupId can be specified to restore a specific backup, otherwise the latest one will be restored.\n");
        sb.append( "\t\t -l list available backups\n");
        sb.append( "\t\t -d delete backups\n");
        sb.append( "\tparameters: \n" );
        sb.append( "\t\t --backupDir <dir> database directory (required for backup)\n");   
        sb.append( "\t\t --restoreDir <dir> backup directory (required for all operations)\n");       
        sb.append( "\t\t --dbDir <dir> restore directory (required for restore)\n");
        sb.append( "\t\t --backupId <num> backup id (required for delete and optional for restore)\n");
        sb.append( "" );
        return sb.toString();
    }

    public void validate() throws ParameterException{
        if(backupOp) {
            if(restoreOp||listOp||deleteOp) {
                throw new ParameterException("Only one of -b, -r, -l or -d can be specified");
            }
            if(dbDir==null) {
                throw new ParameterException("Please specify the database directory (--dbDir)");
            }
            if(yamcsConn!=null) {
                if(yamcsConn.getInstance()==null) {
                    throw new ParameterException("Please specify the yamcs instance in the yamcs connection url (--url)");
                }
            }
        } else if(restoreOp) {
            if(listOp||deleteOp) {
                throw new ParameterException("Only one of -b, -r, -l or -d can be specified");
            }
            
            if(restoreDir==null) {
                throw new ParameterException("Please specify the restore directory (--restoreDir)");
            }

        } else if(listOp) {
            if(deleteOp) {
                throw new ParameterException("Only one of -b, -r, -l or -d can be specified");
            }


        } else if(deleteOp) {
            if(backupId==null) {
                throw new ParameterException("Please specify the backup id(--backupId)");
            }

        } else {
            throw new ParameterException("Please specify one of -b, -r, -l or -d");
        }

        if(backupDir==null) {
            throw new ParameterException("Please specify the backup directory (--backupDir)");
        }
    }
    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        if(backupOp) {
            executeBackup();
        } else if(restoreOp) {
            executeRestore();
        } else if(listOp) {
            executeList();
        } else if(deleteOp) {
            executeDelete();
        }

    }
    public static void verifyBackupDirectory(String backupDir, boolean mustExist) throws Exception {
        Path path = FileSystems.getDefault().getPath(backupDir);
        if(Files.exists(path)) {
            if(!Files.isDirectory(path)) {
                throw new Exception("File '"+backupDir+"' exists and is not a directory");
            }

            boolean isEmpty = true;
            boolean isBackupDir = false;
            try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
                for(Path p: dirStream) {
                    isEmpty = false;
                    if(p.endsWith("LATEST_BACKUP")) {
                        isBackupDir = true;
                        break;
                    }
                }
            }    

            if(!isEmpty && !isBackupDir) {
                throw new Exception("Directory '"+backupDir+"' is not a backup directory");
            }
            if(!Files.isWritable(path)) {
                throw new Exception("Directory '"+backupDir+"' is not writable");
            }
        } else {
            if(mustExist) {
                throw new Exception("Directory '"+backupDir+"' does not exist");
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

    private void executeBackup() throws Exception {
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

    private void executeList() throws Exception {
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

    private void executeRestore() throws Exception {
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

    private void executeDelete() throws Exception {
        verifyBackupDirectory(backupDir, true);
        BackupableDBOptions opt = new BackupableDBOptions(backupDir);
        BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
        backupEngine.deleteBackup(backupId);
        backupEngine.close();
        console.println("Backup with id "+backupId+" removed");
    }

}