package org.yamcs.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.rocksdb.BackupEngine;
import org.rocksdb.BackupEngineOptions;
import org.rocksdb.BackupInfo;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.Options;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.yamcs.YamcsServer;
import org.yamcs.yarch.BackupControlMBean;
import org.yamcs.yarch.BackupUtils;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.PathConverter;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

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
public class BackupCli extends Command {
    public BackupCli(YamcsAdminCli yamcsCli) {
        super("backup", yamcsCli);
        addSubCommand(new BackupCreate());
        addSubCommand(new BackupDelete());
        addSubCommand(new BackupList());
        addSubCommand(new BackupRestore());
        addSubCommand(new BackupPurge());
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

        @Parameter(names = "--backup-dir", description = "Directory containing backups", required = true)
        String backupDir;

    }

    @Parameters(commandDescription = "Create a new backup.")
    private class BackupCreate extends BackupCommand {

        @Parameter(names = "--data-dir", description = "Yamcs Data directory", converter = PathConverter.class)
        Path dataDir;

        @Parameter(names = "--host", description = "Trigger a hot backup over JMX")
        String jmxAddress;

        @Parameter(names = "--pid", description = "Process identifier of a Yamcs server")
        String pid;

        @Parameter(description = "TABLESPACE", required = true)
        List<String> mainParameter;

        public BackupCreate() {
            super("create", BackupCli.this);
        }

        @Override
        void execute() throws Exception {
            if (mainParameter.size() > 1) {
                throw new IllegalArgumentException("Only one tablespace can be backed up at a time");
            }
            String tablespace = mainParameter.get(0);
            if (dataDir != null) {
                Path tablespaceDir = dataDir.resolve(tablespace + ".rdb");
                Path current = tablespaceDir.resolve("CURRENT");
                if (!Files.exists(current)) {
                    throw new IllegalArgumentException("'" + tablespaceDir
                            + "' does not look like a tablespace, the CURRENT file does not exist inside");
                }
                BackupUtils.verifyBackupDirectory(backupDir, false);
                try (Options opt = new Options();
                        BackupEngineOptions bopt = new BackupEngineOptions(backupDir);
                        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
                        DBOptions dbOptions = new DBOptions();
                        BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), bopt);) {

                    List<byte[]> cfl = RocksDB.listColumnFamilies(opt, tablespaceDir.toString());
                    List<ColumnFamilyDescriptor> cfdList = new ArrayList<>(cfl.size());

                    for (byte[] b : cfl) {
                        cfdList.add(new ColumnFamilyDescriptor(b, cfOptions));
                    }
                    List<ColumnFamilyHandle> cfhList = new ArrayList<>(cfl.size());

                    try (RocksDB db = RocksDB.open(dbOptions, tablespaceDir.toString(), cfdList, cfhList)) {
                        backupEngine.createNewBackup(db);
                        console.println("Backup performed successfully");
                    } finally {
                        for (final ColumnFamilyHandle cfh : cfhList) {
                            cfh.close();
                        }
                    }
                } catch (RocksDBException e) {
                    throw new IOException(
                            "Error when backing up tablespace '" + tablespace + "' to '" + backupDir + "': "
                                    + e.toString());
                }
            } else if (jmxAddress != null) {
                JMXServiceURL jmxServiceUrl = new JMXServiceURL(String.format(
                        "service:jmx:rmi:///jndi/rmi://%s/jmxrmi", jmxAddress));
                JMXConnector jmxc = JMXConnectorFactory.connect(jmxServiceUrl);
                MBeanServerConnection conn = jmxc.getMBeanServerConnection();
                ObjectName mbeanName = new ObjectName("org.yamcs:name=Backup");
                BackupControlMBean control = JMX.newMBeanProxy(conn, mbeanName, BackupControlMBean.class);
                control.createBackup(tablespace, backupDir);
            } else {
                String jvmIdentifier = pid;
                if (jvmIdentifier == null) {
                    // Find a single local runnning Yamcs
                    for (VirtualMachineDescriptor vmDescriptor : VirtualMachine.list()) {
                        // Something of the form:
                        // org.yamcs.YamcsServer --data-dir /storage/yamcs-data
                        var displayName = vmDescriptor.displayName();
                        if (displayName.startsWith(YamcsServer.class.getName())) {
                            if (jvmIdentifier != null) {
                                throw new YamcsAdminException(
                                        "More than one Yamcs server is running. Specify --pid");
                            }
                            jvmIdentifier = vmDescriptor.id();
                        }
                    }
                }

                if (jvmIdentifier == null) {
                    throw new YamcsAdminException("Cannot connect to Yamcs. "
                            + "Use --data-dir if you want to perform an offline backup");
                }

                VirtualMachine vm = VirtualMachine.attach(jvmIdentifier);
                try {
                    String jmxAddress = vm.startLocalManagementAgent();
                    JMXServiceURL url = new JMXServiceURL(jmxAddress);
                    JMXConnector jmxc = JMXConnectorFactory.connect(url);
                    MBeanServerConnection conn = jmxc.getMBeanServerConnection();
                    ObjectName mbeanName = new ObjectName("org.yamcs:name=Backup");
                    BackupControlMBean control = JMX.newMBeanProxy(conn, mbeanName, BackupControlMBean.class);
                    control.createBackup(tablespace, backupDir);
                } finally {
                    vm.detach();
                }
            }
        }
    }

    @Parameters(commandDescription = "Restore a backup. This can only be done when Yamcs is not running.")
    private class BackupRestore extends BackupCommand {

        @Parameter(names = "--restore-dir", description = "Directory where to restore the backup", required = true)
        String restoreDir;

        @Parameter(description = "ID", required = true)
        List<String> mainParameters;

        public BackupRestore() {
            super("restore", BackupCli.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                    RestoreOptions restoreOpt = new RestoreOptions(true);) {
                if (mainParameters.size() > 1) {
                    throw new IllegalArgumentException("Too many arguments. Only one backup can be restored");
                } else if (mainParameters.size() == 1) {
                    int backupId = Integer.parseInt(mainParameters.get(0));
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
            super("list", BackupCli.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);

            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt)) {
                TableStringBuilder b = new TableStringBuilder("backup id", "size (bytes)", "num files", "time");
                final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
                for (BackupInfo bi : backupEngine.getBackupInfo()) {
                    b.addLine(bi.backupId(), bi.size(), bi.numberFiles(),
                            formatter.format(Instant.ofEpochMilli(1000 * bi.timestamp())));
                }
                console.println(b.toString());
            }
        }
    }

    @Parameters(commandDescription = "Delete a backup")
    public class BackupDelete extends BackupCommand {

        @Parameter(description = "ID ...", required = true)
        List<String> mainParameters;

        public BackupDelete() {
            super("delete", BackupCli.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);) {

                for (String mainParameter : mainParameters) {
                    int backupId = Integer.parseInt(mainParameter);
                    backupEngine.deleteBackup(backupId);
                    console.println("Deleted backup " + backupId);
                }
            }
        }
    }

    @Parameters(commandDescription = "Purge old backups")
    public class BackupPurge extends BackupCommand {

        @Parameter(names = "--keep", description = "Number of backups to keep", required = true)
        Integer backupsToKeep;

        public BackupPurge() {
            super("purge", BackupCli.this);
        }

        @Override
        void execute() throws Exception {
            BackupUtils.verifyBackupDirectory(backupDir, true);
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);) {
                backupEngine.purgeOldBackups(backupsToKeep);
                int n = backupEngine.getBackupInfo().size();
                console.println("Purged operation successful; " + n + " backups remaining.");
            }
        }
    }
}
