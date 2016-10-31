package org.yamcs.yarch.rocksdb;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommandHandler;
import org.kohsuke.args4j.spi.SubCommands;

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
public class BackupCli {    
    @Argument(required=true, metaVar="command", usage="subcommands {backup|list|restore}", handler=SubCommandHandler.class)
    @SubCommands({
        @SubCommand(name="list", impl=List.class),
        @SubCommand(name="restore", impl=Restore.class),
        @SubCommand(name="backup", impl=Backup.class)
    })
    Command cmd;
    public static void printUsage() {
        System.out.println( "Usage: " );
        System.out.println( "\tbackup.sh [command] [params] " );
        System.out.println( "Commands: " );
        System.out.println( "\tbackup\tbackup a database");
        System.out.println( "\tlist\tlist the available backups");
        System.out.println( "\trestore\trestore a backup");
        System.out.println( "" );

    }


    public static void main(String[] args) {
        BackupCli backupCli = new BackupCli();
        try {
            new CmdLineParser(backupCli).parseArgument(args);
            backupCli.cmd.execute();
        } catch( CmdLineException e ) {
            System.err.println("cmd: "+backupCli.cmd);
            System.err.println(e.getMessage());
            printUsage();
            return;
        }
    }


    public static abstract class Command {
        abstract void execute(); 
    }

    public static class Restore extends Command {
        @Option(name="-bd", usage="backup directory", required=true)
        String backupDir;
        
        @Option(name="-td", usage="target directory (where the backup will be restored)", required=true)
        String targetDir;
        
        @Override
        void execute() {
            System.out.println("in restore execute backupDir: "+backupDir+" targetDir: "+targetDir);
        }

    }

    public static class List extends Command {
        @Option(name="-bd", usage="backup directory",required=true)
        String backupDir;

        @Option(name="-h", usage="help", help=true)
        boolean help;
        
        @Override
        void execute() {
            System.out.println("in list execute backupDir: "+backupDir);
        }

    }
    public static class Backup extends Command {
        @Option(name="-bd", required=true)
        String backupDir;

        @Override
        void execute() {
            System.out.println("in list execute backupDir: "+backupDir);
        }

    }
}