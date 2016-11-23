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
import org.yamcs.cli.YamcsCli.Command;
import org.yamcs.yarch.rocksdb.YRDB;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;


/**
 * Command line utility for doing rocksdb operations
 * 
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "RocksDB operations")
public class RocksDbCli extends Command {

    @Parameter(names="-c", description="compact database")
    private boolean consolidateOp;

    @Parameter(names="-h", description="help")
    private boolean helpOp;

    
    @Parameter(names="--dbDir", description="database directory")
    String dbDir;


    public String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append( "Usage: \n" );
        sb.append( "\t rocksdb <operation> [parameters] \n" );
        sb.append( "\t operation: \n");
        sb.append( "\t\t -c Consolidate database. Merge all sst files into one single file with the highest level.\n");
        sb.append( "\t\t -h Print this usage information.\n");
        sb.append( "\tparameters: \n" );
        sb.append( "\t\t --dbDir <dir> database directory\n");
        
        sb.append( "" );
        return sb.toString();
    }

    public void validate() throws ParameterException{
        if(consolidateOp) {
        } else {
            throw new ParameterException("Please specify one of -c");
        }
        if(dbDir==null) {
            throw new ParameterException("Please specify the database directory (--dbDir)");
        }

    }
    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        if(consolidateOp) {
          consolidate();
        } 

    }
  
    private void consolidate() throws Exception {
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