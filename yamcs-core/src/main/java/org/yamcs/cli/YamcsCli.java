package org.yamcs.cli;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Console;

/**
 * Command line utility for doing yamcs stuff.
 * 
 * This usage is yamcs <command> [command_specific_options]
 * 
 * The commands implemented currently are:
 *   backup
 * 
 * @author nm
 *
 */
public class YamcsCli {    
    static Map<String, Command> commands = new HashMap<>();
    
    @Parameter(names="-y", description="yamcs url")
    private String yamcsUrl;
    
    @Parameter(names="-h", description="help")
    private boolean helpOp;
    static Console console = JCommander.getConsole();
    
    public static void printUsage() {
        console.println( "Usage: " );
        console.println( "\tyamcs [-y <url>] command [params] " );
        console.println( "\turl is the URL where yamcs can be reached (but not all commands require a connection to a live server)." );
        console.println( "Commands: " );
        console.println( "\tbackup\tprovides functionality for backing up and restoring databases");
        console.println( "\trocksdb\tprovides low level utitlities for manipulating rocksdb databases");
        console.println( "\tarchive\tprovides utitlities for manipulating yamcs archive");
        console.println( "" );

    }

    public static void main(String[] args) {
        YamcsCli yamcsCli = new YamcsCli();
        JCommander jc = new JCommander(yamcsCli);
        Backup backup = new Backup();
        commands.put("backup", backup);
        jc.addCommand("backup", backup);
        
        RocksDbCli rocksdb = new RocksDbCli();
        commands.put("rocksdb", rocksdb);
        jc.addCommand("rocksdb", rocksdb);
        
        ArchiveCli archive = new ArchiveCli();
        commands.put("archive", archive);
        jc.addCommand("archive", archive);
        
        
        try {
            jc.parse(args);
        } catch (ParameterException e) {
            console.println(e.getMessage());
            printUsage();
            System.exit(1);
        } 
        
        if(jc.getParsedCommand()==null) {
            printUsage();
            System.exit(1);
        }
        
        Command cmd = commands.get(jc.getParsedCommand());
        try {
            if(yamcsCli.yamcsUrl!=null) {
                cmd.yamcsConn = YamcsConnectionProperties.parse(yamcsCli.yamcsUrl);
            }
            cmd.console = console;
        } catch (URISyntaxException e1) {
            System.err.println("cannot parse yamcs URL: '"+yamcsCli.yamcsUrl+"'");
        }
        
        try {
            cmd.validate();
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println(cmd.getUsage());
            System.exit(1);
        }
        try {
            cmd.execute();
        } catch (ParameterException e) {
            System.err.println("ERROR: "+ e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }


    public static abstract class Command {
        protected Console console;
        protected YamcsConnectionProperties yamcsConn;
        
        abstract void execute() throws Exception;
        abstract void validate() throws ParameterException;
        abstract String getUsage();
    }

}