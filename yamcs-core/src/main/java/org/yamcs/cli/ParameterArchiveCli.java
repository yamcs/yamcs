package org.yamcs.cli;

import java.util.HashMap;

import org.rocksdb.RocksDB;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;


/**
 * Command line utility for doing general archive operations
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Parameter Archive operations (works offline, the yamcs server has to be stopped)")
public class ParameterArchiveCli extends Command {
    
    @Parameter(names="--instance", description="yamcs instance", required=true)
    String yamcsInstance;
    
    public ParameterArchiveCli(YamcsCli yamcsCli) {
        super("parchive", yamcsCli);
        addSubCommand(new PrintMetadata());
        TimeEncoding.setUp();
    }
    
    @Parameters(commandDescription = "Print parameter name to parameter id mapping")
    class PrintMetadata extends Command {
        PrintMetadata() {
            super("print-pid", ParameterArchiveCli.this);
        }
        
        @Override
        public void execute() throws Exception {
            RocksDB.loadLibrary();
            ParameterArchive parchive = new ParameterArchive(yamcsInstance, new HashMap<String, Object>());
            ParameterIdDb pid = parchive.getParameterIdDb();
            pid.print(System.out);
        }
    }
}