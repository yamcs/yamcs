package org.yamcs.server.cli;

import java.util.HashMap;

import org.rocksdb.RocksDB;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Command line utility for doing general archive operations
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Parameter Archive operations")
public class ParameterArchiveCli extends Command {

    @Parameter(names = "--instance", description = "yamcs instance", required = true)
    String yamcsInstance;

    public ParameterArchiveCli(YamcsCtlCli yamcsCli) {
        super("parchive", yamcsCli);
        addSubCommand(new PrintPid());
        addSubCommand(new PrintPgid());
        TimeEncoding.setUp();
    }

    @Parameters(commandDescription = "Print parameter name to parameter id mapping")
    class PrintPid extends Command {
        PrintPid() {
            super("print-pid", ParameterArchiveCli.this);
        }

        @Override
        public void execute() throws Exception {
            RocksDB.loadLibrary();
            ParameterArchiveV2 parchive = new ParameterArchiveV2(yamcsInstance, new HashMap<String, Object>());
            ParameterIdDb pid = parchive.getParameterIdDb();
            pid.print(System.out);
        }
    }

    @Parameters(commandDescription = "Print parameter group compositions")
    class PrintPgid extends Command {
        @Parameter(names = "--name", description = "fully qualified name of the parameter", required = true)
        String name;

        PrintPgid() {
            super("print-pgid", ParameterArchiveCli.this);
        }

        @Override
        public void execute() throws Exception {
            RocksDB.loadLibrary();
            ParameterArchiveV2 parchive = new ParameterArchiveV2(yamcsInstance, new HashMap<String, Object>());
            ParameterIdDb pid = parchive.getParameterIdDb();
            ParameterGroupIdDb pgid = parchive.getParameterGroupIdDb();
            ParameterId[] pids = pid.get(name);
            if (pids == null) {
                console.println("No parameter named '" + name + "' in the parameter archive");
                return;
            }
            for (ParameterId p : pids) {
                console.println("groups for " + p + ": ");
                int[] groups = pgid.getAllGroups(p.pid);
                for (int g : groups) {
                    console.print(g + ": ");
                    SortedIntArray sia = pgid.getParameterGroup(g);
                    for (int a : sia.getArray()) {
                        console.print(pid.getParameterFqnById(a) + " ");
                    }
                    console.println("");
                }
            }
        }
    }
}
