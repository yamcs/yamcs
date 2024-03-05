package org.yamcs.cli;

import java.util.ArrayList;
import java.util.List;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionStyle;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.yamcs.yarch.rocksdb.YRDB;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Command line utility for doing rocksdb operations
 * 
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Provides low-level RocksDB data operations")
public class RocksDbCli extends Command {

    public RocksDbCli(YamcsAdminCli yamcsCli) {
        super("rocksdb", yamcsCli);
        addSubCommand(new RocksDbCompact());
        addSubCommand(new RocksDbBenchmark(this));
    }

    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        super.execute();
    }

    @Parameters(commandDescription = "Compact RocksDB database")
    private class RocksDbCompact extends Command {
        @Parameter(names = "--dbDir", description = "database directory", required = true)
        String dbDir;

        @Parameter(names = "--sizeMB", description = "target size of each SST files in MB (by default 256 MB)", required = false)
        int sizeMB = 256;

        public RocksDbCompact() {
            super("compact", RocksDbCli.this);
        }

        @Override
        void execute() throws Exception {

            Options opt = new Options();
            List<byte[]> cfl = RocksDB.listColumnFamilies(opt, dbDir);
            List<ColumnFamilyDescriptor> cfdList = new ArrayList<>(cfl.size());
            ColumnFamilyOptions cfoptions = new ColumnFamilyOptions();

            cfoptions.setCompactionStyle(CompactionStyle.UNIVERSAL);
            cfoptions.setTargetFileSizeBase(1024L * 1024 * sizeMB);
            cfoptions.setTargetFileSizeMultiplier(1);
            for (byte[] b : cfl) {
                cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));
            }
            List<ColumnFamilyHandle> cfhList = new ArrayList<>(cfl.size());

            try (DBOptions dbOptions = new DBOptions();
                    RocksDB db = RocksDB.open(dbOptions, dbDir, cfdList, cfhList)) {
                for (int i = 0; i < cfhList.size(); i++) {
                    ColumnFamilyHandle cfh = cfhList.get(i);
                    console.println("Compacting Column Family " + YRDB.cfNameToString(cfl.get(i)));
                    db.compactRange(cfh);
                }
            }
            cfoptions.close();
            opt.close();
        }
    }
}
