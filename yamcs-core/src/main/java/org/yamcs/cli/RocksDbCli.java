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

    public RocksDbCli(YamcsCli yamcsCli) {
        super("rocksdb", yamcsCli);
        addSubCommand(new RocksdbCompact());
    }


    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        super.execute();
    }

    @Parameters(commandDescription = "Compact rocksdb database")
    private class RocksdbCompact extends Command {
        @Parameter(names="--dbDir", description="database directory", required=true)
        String dbDir;

        public RocksdbCompact() {
            super("compact", RocksDbCli.this);
        }

        @Override
        void execute() throws Exception {
            Options opt = new Options();
            List<byte[]> cfl = RocksDB.listColumnFamilies(opt, dbDir);
            List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());
            ColumnFamilyOptions cfoptions = new ColumnFamilyOptions();
            cfoptions.setCompactionStyle(CompactionStyle.UNIVERSAL);

            for(byte[] b: cfl) {
                cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));                                      
            }
            List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
            DBOptions dbOptions = new DBOptions();

            RocksDB db = RocksDB.open(dbOptions, dbDir, cfdList, cfhList);
            for(int i=0;i<cfhList.size(); i++) {
                ColumnFamilyHandle cfh = cfhList.get(i);
                console.println("Compacting Column Family "+YRDB.cfNameToString(cfl.get(i)));
                db.compactRange(cfh);
            }
            db.close();
            dbOptions.close();
            cfoptions.close();
            opt.close();
        }
    }
}